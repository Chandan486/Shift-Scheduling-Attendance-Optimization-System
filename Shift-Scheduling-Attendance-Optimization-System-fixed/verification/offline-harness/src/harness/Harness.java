package harness;

import com.shiftscheduling.leave.dto.*;
import com.shiftscheduling.leave.entity.*;
import com.shiftscheduling.leave.outbox.NotificationOutboxService;
import com.shiftscheduling.leave.repository.*;
import com.shiftscheduling.leave.service.LeaveService;
import com.shiftscheduling.scheduling.client.EmployeeDirectoryClient;
import com.shiftscheduling.scheduling.client.RemoteEmployee;
import com.shiftscheduling.scheduling.dto.NotificationPayload;
import com.shiftscheduling.scheduling.entity.Shift;
import com.shiftscheduling.scheduling.entity.ShiftAssignment;
import com.shiftscheduling.scheduling.repository.ShiftAssignmentRepository;
import com.shiftscheduling.scheduling.service.ShiftAssignmentService;
import com.shiftscheduling.scheduling.security.RequestUser;
import com.shiftscheduling.notification.dto.LeaveDecisionRequest;
import com.shiftscheduling.scheduling.dto.ShiftAssignmentResponse;
import com.shiftscheduling.notification.dto.*;
import com.shiftscheduling.gateway.filter.AccessRules;
import com.shiftscheduling.notification.entity.*;
import com.shiftscheduling.notification.exception.ForbiddenException;
import com.shiftscheduling.notification.repository.NotificationRepository;
import com.shiftscheduling.notification.service.NotificationService;

import java.lang.reflect.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;

/**
 * Offline scenario harness. Runs the REAL LeaveService, ShiftAssignmentService and NotificationService
 * code. Only infrastructure is replaced: repositories are dynamic proxies over in-memory maps, the
 * outbox records what it would send, and the employee directory is scenario data.
 */
public class Harness {

    static int passed = 0, failed = 0;

    static void check(String name, boolean ok) {
        if (ok) { passed++; System.out.println("PASS  " + name); }
        else    { failed++; System.out.println("FAIL  " + name); }
    }

    /** Runs code expected to be refused; returns "STATUS: message" or null when it did not fail. */
    static String refusal(Runnable r) {
        try { r.run(); return null; }
        catch (RuntimeException e) {
            // ForbiddenException carries no status; notification-service's handler maps it to 403.
            if (e instanceof ForbiddenException) return "FORBIDDEN: " + e.getMessage();
            try {
                Object st = e.getClass().getMethod("getStatus").invoke(e);
                return st + ": " + e.getMessage();
            } catch (ReflectiveOperationException x) {
                return "RUNTIME: " + e.getMessage();
            }
        }
    }

    static void expectRefused(String name, Runnable r, String status, String messagePart) {
        String got = refusal(r);
        check(name + "  [got " + got + "]", got != null && got.startsWith(status + ":") && (messagePart == null || got.contains(messagePart)));
    }

    /** Dynamic repository proxy: unstubbed methods fail loudly so a missing stub can never pass silently. */
    @SuppressWarnings("unchecked")
    static <T> T repo(Class<T> iface, Map<String, Function<Object[], Object>> handlers) {
        return (T) Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface}, (p, m, args) -> {
            if (m.getDeclaringClass() == Object.class) return m.invoke(handlers, args);
            Function<Object[], Object> f = handlers.get(m.getName());
            if (f == null) throw new UnsupportedOperationException("unstubbed repository method " + m.getName());
            return f.apply(args);
        });
    }

    static void setField(Object target, String field, Object value) {
        try { Field f = target.getClass().getDeclaredField(field); f.setAccessible(true); f.set(target, value); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    static Object getField(Object target, String field) {
        try { Field f = target.getClass().getDeclaredField(field); f.setAccessible(true); return f.get(target); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    public static void main(String[] args) {
        contractChecks();
        leaveWorkflows();
        shiftAssignmentWorkflows();
        notificationWorkflows();
        departmentAndRevalidationChecks();
        accessRuleChecks();
        System.out.println();
        System.out.println("RESULT: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    // ------------------------------------------------------------------
    // 0. Contracts between services: JSON is matched by record component name, so the names must agree.
    // ------------------------------------------------------------------
    static void contractChecks() {
        List<String> leaveOut = componentNames(LeaveDecisionNotification.class);
        List<String> leaveIn = componentNames(com.shiftscheduling.notification.dto.LeaveDecisionRequest.class);
        check("contract: leave decision payload fields == notification-service LeaveDecisionRequest fields", leaveOut.equals(leaveIn));
        List<String> notifOut = componentNames(NotificationPayload.class);
        List<String> notifIn = componentNames(NotificationRequest.class);
        check("contract: shift notification payload fields == notification-service NotificationRequest fields", notifOut.equals(notifIn));
        check("contract: LeaveApprovalRequest exposes 'status' and 'reviewNotes' (frontend sends these)",
                componentNames(LeaveApprovalRequest.class).equals(List.of("status", "reviewNotes")));
    }

    static List<String> componentNames(Class<?> c) {
        List<String> names = new ArrayList<>();
        for (RecordComponent rc : c.getRecordComponents()) names.add(rc.getName());
        return names;
    }

    // ------------------------------------------------------------------
    // 1. Leave approval / rejection, using the real LeaveService
    // ------------------------------------------------------------------
    static final com.shiftscheduling.leave.security.RequestUser HR = new com.shiftscheduling.leave.security.RequestUser(20L, "HR", "Priya HR");
    static final com.shiftscheduling.leave.security.RequestUser HR_OTHER = new com.shiftscheduling.leave.security.RequestUser(21L, "HR", "Other HR");
    static final com.shiftscheduling.leave.security.RequestUser ADMIN_A = new com.shiftscheduling.leave.security.RequestUser(10L, "ADMIN", "Admin One");
    static final com.shiftscheduling.leave.security.RequestUser ADMIN_B = new com.shiftscheduling.leave.security.RequestUser(11L, "ADMIN", "Admin Two");

    static LeaveRequest leave(long id, long applicant, String role, LeaveType type, int days) {
        LeaveRequest r = new LeaveRequest();
        setField(r, "id", id);
        r.setEmployeeId(applicant);
        r.setApplicantUserId(applicant);
        r.setApplicantRole(role);
        r.setApplicantName("Applicant " + applicant);
        r.setLeaveType(type);
        r.setStartDate(LocalDate.of(2027, 3, 1));
        r.setEndDate(LocalDate.of(2027, 3, days));
        r.setReason("Family event, need time off");
        r.setStatus(LeaveStatus.PENDING);
        return r;
    }

    static LeaveBalance balance(long employee, int annualRemaining) {
        LeaveBalance b = new LeaveBalance();
        b.setEmployeeId(employee); b.setYear(2027);
        b.setAnnualLeave(20); b.setAnnualUsed(20 - annualRemaining);
        b.setSickLeave(10); b.setPersonalLeave(5);
        return b;
    }

    static void leaveWorkflows() {
        Map<Long, LeaveRequest> leaves = new LinkedHashMap<>();
        Map<String, LeaveBalance> balances = new HashMap<>();
        List<String> locks = new ArrayList<>();
        NotificationOutboxService outbox = new NotificationOutboxService();

        Map<String, Function<Object[], Object>> lr = new HashMap<>();
        lr.put("findByIdForUpdate", a -> { locks.add("lock:" + a[0]); return Optional.ofNullable(leaves.get((Long) a[0])); });
        lr.put("save", a -> { LeaveRequest r = (LeaveRequest) a[0]; leaves.put((Long) getField(r, "id"), r); return r; });
        lr.put("findByStatus", a -> leaves.values().stream().filter(r -> r.getStatus() == LeaveStatus.PENDING).toList());
        lr.put("findByEmployeeIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual", a -> {
            Long emp = (Long) a[0]; Collection<?> statuses = (Collection<?>) a[1];
            LocalDate end = (LocalDate) a[2], start = (LocalDate) a[3];
            return leaves.values().stream().filter(r -> r.getEmployeeId().equals(emp)
                    && statuses.contains(r.getStatus())
                    && !r.getStartDate().isAfter(end) && !r.getEndDate().isBefore(start)).toList();
        });
        LeaveRequestRepository leaveRepo = repo(LeaveRequestRepository.class, lr);

        Map<String, Function<Object[], Object>> br = new HashMap<>();
        br.put("findByEmployeeIdAndYearForUpdate", a -> Optional.ofNullable(balances.get(a[0] + "/" + a[1])));
        br.put("findByEmployeeIdAndYear", a -> Optional.ofNullable(balances.get(a[0] + "/" + a[1])));
        br.put("save", a -> { LeaveBalance b = (LeaveBalance) a[0]; balances.put(b.getEmployeeId() + "/" + b.getYear(), b); return b; });
        LeaveBalanceRepository balanceRepo = repo(LeaveBalanceRepository.class, br);

        LeaveService svc = new LeaveService(leaveRepo, balanceRepo, outbox);

        // Seed: employee 5, HR 20, admin 10, plus balances.
        balances.put("5/2027", balance(5, 10));
        balances.put("20/2027", balance(20, 10));
        balances.put("10/2027", balance(10, 10));

        // --- Employee request: only HR may decide ---
        leaves.put(100L, leave(100, 5, "EMPLOYEE", LeaveType.ANNUAL, 3));
        check("leave: employee leave is refused when decided by ADMIN",
                refusal(() -> svc.decide(100L, new LeaveApprovalRequest(LeaveStatus.APPROVED, null), ADMIN_A)) != null);
        LeaveResponse out = svc.decide(100L, new LeaveApprovalRequest(LeaveStatus.APPROVED, null), HR);
        check("leave: employee leave approved by HR -> APPROVED with reviewer recorded", out.status() == LeaveStatus.APPROVED && out.reviewedBy() == 20L);
        check("leave: approval deducted 3 annual days from the applicant's balance", balances.get("5/2027").getAnnualUsed() == 13);
        check("leave: decision time recorded", out.reviewedAt() != null);
        LeaveDecisionNotification n1 = lastDecision(outbox);
        check("leave: approval notification goes to the APPLICANT (5), not the approver (20)", n1 != null && n1.userId() == 5L && n1.approved());
        check("leave: approval notification names the approver", n1 != null && "Priya HR".equals(n1.approverName()));
        check("leave: approval is persisted under a row lock", locks.contains("lock:100"));

        // Repeat decision: refused, no second deduction, no second notification.
        int notifsBefore = outbox.enqueued.size();
        expectRefused("leave: repeated decision on an approved request is refused (409)",
                () -> svc.decide(100L, new LeaveApprovalRequest(LeaveStatus.APPROVED, null), HR), "CONFLICT", "already been approved");
        check("leave: repeat decision did not deduct balance again", balances.get("5/2027").getAnnualUsed() == 13);
        check("leave: repeat decision did not enqueue another notification", outbox.enqueued.size() == notifsBefore);

        // --- Rejection needs a reason; reason reaches the applicant ---
        leaves.put(101L, leave(101, 5, "EMPLOYEE", LeaveType.SICK, 1));
        expectRefused("leave: rejection without a reason is refused (400)",
                () -> svc.decide(101L, new LeaveApprovalRequest(LeaveStatus.REJECTED, "   "), HR), "BAD_REQUEST", "reason");
        check("leave: request stays PENDING after a refused rejection", leaves.get(101L).getStatus() == LeaveStatus.PENDING);
        LeaveResponse rej = svc.decide(101L, new LeaveApprovalRequest(LeaveStatus.REJECTED, "Team short that week"), HR);
        LeaveDecisionNotification n2 = lastDecision(outbox);
        check("leave: rejection stores REJECTED with the reason", rej.status() == LeaveStatus.REJECTED && "Team short that week".equals(rej.reviewNotes()));
        check("leave: rejection notification carries the reason and goes to the applicant",
                n2 != null && !n2.approved() && "Team short that week".equals(n2.reason()) && n2.userId() == 5L);
        check("leave: rejection did not touch the balance", balances.get("5/2027").getAnnualUsed() == 13);

        // --- Insufficient balance at approval time ---
        leaves.put(102L, leave(102, 5, "EMPLOYEE", LeaveType.ANNUAL, 9));  // only 10-3=7 left
        expectRefused("leave: approval refused when the balance no longer covers it (409)",
                () -> svc.decide(102L, new LeaveApprovalRequest(LeaveStatus.APPROVED, null), HR), "CONFLICT", "Cannot approve");
        check("leave: insufficient-balance refusal leaves the request PENDING", leaves.get(102L).getStatus() == LeaveStatus.PENDING);

        // --- HR request: only an ADMIN other than the applicant, never HR ---
        leaves.put(200L, leave(200, 20, "HR", LeaveType.ANNUAL, 2));
        expectRefused("leave: HR leave cannot be decided by another HR",
                () -> svc.decide(200L, new LeaveApprovalRequest(LeaveStatus.APPROVED, null), HR_OTHER), "FORBIDDEN", "Only ADMIN");
        expectRefused("leave: HR cannot approve their own leave",
                () -> svc.decide(200L, new LeaveApprovalRequest(LeaveStatus.APPROVED, null), new com.shiftscheduling.leave.security.RequestUser(20L, "ADMIN", "x")), "FORBIDDEN", "own leave");
        LeaveResponse hrOk = svc.decide(200L, new LeaveApprovalRequest(LeaveStatus.APPROVED, null), ADMIN_B);
        check("leave: HR leave approved by a different ADMIN", hrOk.status() == LeaveStatus.APPROVED && hrOk.reviewedBy() == 11L);
        check("leave: HR applicant's notification goes to the HR user (20)", lastDecision(outbox).userId() == 20L);

        // --- Admin request: a different ADMIN only ---
        leaves.put(300L, leave(300, 10, "ADMIN", LeaveType.PERSONAL, 1));
        expectRefused("leave: admin cannot approve their own leave",
                () -> svc.decide(300L, new LeaveApprovalRequest(LeaveStatus.APPROVED, null), ADMIN_A), "FORBIDDEN", "different authorised ADMIN");
        expectRefused("leave: admin leave cannot be decided by HR",
                () -> svc.decide(300L, new LeaveApprovalRequest(LeaveStatus.REJECTED, "no"), HR), "FORBIDDEN", "Only ADMIN");
        LeaveResponse adminOk = svc.decide(300L, new LeaveApprovalRequest(LeaveStatus.REJECTED, "Please reschedule"), ADMIN_B);
        check("leave: admin leave rejected by a different ADMIN with reason", adminOk.status() == LeaveStatus.REJECTED);
        check("leave: admin applicant gets the rejection (user 10)", lastDecision(outbox).userId() == 10L && !lastDecision(outbox).approved());

        // --- Queues: who sees what ---
        leaves.put(400L, leave(400, 5, "EMPLOYEE", LeaveType.ANNUAL, 1));
        leaves.put(401L, leave(401, 10, "ADMIN", LeaveType.ANNUAL, 1));
        leaves.put(402L, leave(402, 20, "HR", LeaveType.ANNUAL, 1));
        check("queue: HR sees only pending EMPLOYEE requests",
                svc.getPendingLeavesFor(HR).stream().allMatch(l -> "EMPLOYEE".equals(l.applicantRole())) && !svc.getPendingLeavesFor(HR).isEmpty());
        check("queue: admin sees HR/admin requests from others but not their own",
                svc.getPendingLeavesFor(ADMIN_B).stream().noneMatch(l -> l.applicantUserId() == 11L)
                        && svc.getPendingLeavesFor(ADMIN_B).stream().anyMatch(l -> l.applicantRole() == null || !"EMPLOYEE".equals(l.applicantRole())));
        check("queue: admin A does not see their own pending admin request",
                svc.getPendingLeavesFor(ADMIN_A).stream().noneMatch(l -> l.applicantUserId() == 10L));
        check("queue: an employee gets an empty queue", svc.getPendingLeavesFor(new com.shiftscheduling.leave.security.RequestUser(5L, "EMPLOYEE", "x")).isEmpty());

        // --- Applying ---
        expectRefused("apply: employee cannot submit leave for another user",
                () -> svc.applyLeave(new LeaveRequestDto(7L, LeaveType.ANNUAL, LocalDate.now().plusDays(30), LocalDate.now().plusDays(31), "Wedding in the family"),
                        new com.shiftscheduling.leave.security.RequestUser(5L, "EMPLOYEE", "Me")), "FORBIDDEN", "yourself");
        expectRefused("apply: overlapping pending/approved leave is refused",
                () -> svc.applyLeave(new LeaveRequestDto(5L, LeaveType.UNPAID, LocalDate.of(2027, 3, 2), LocalDate.of(2027, 3, 2), "Overlaps the approved one"),
                        new com.shiftscheduling.leave.security.RequestUser(5L, "EMPLOYEE", "Me")), "CONFLICT", "overlaps");
        LeaveResponse applied = svc.applyLeave(new LeaveRequestDto(5L, LeaveType.UNPAID, LocalDate.of(2027, 8, 2), LocalDate.of(2027, 8, 2), "Personal appointment abroad"),
                new com.shiftscheduling.leave.security.RequestUser(5L, "EMPLOYEE", "Ravi Kumar"));
        check("apply: new request is PENDING and records applicant id, role and name from the token",
                applied.status() == LeaveStatus.PENDING && applied.applicantUserId() == 5L && "EMPLOYEE".equals(applied.applicantRole()) && "Ravi Kumar".equals(applied.applicantName()));
    }

    static LeaveDecisionNotification lastDecision(NotificationOutboxService outbox) {
        if (outbox.enqueued.isEmpty()) return null;
        return (LeaveDecisionNotification) outbox.enqueued.get(outbox.enqueued.size() - 1)[1];
    }

    // ------------------------------------------------------------------
    // 2. Shift assignment, using the real ShiftAssignmentService
    // ------------------------------------------------------------------
    static void shiftAssignmentWorkflows() {
        List<ShiftAssignment> store = new ArrayList<>();
        long[] ids = {1000};
        com.shiftscheduling.scheduling.outbox.NotificationOutboxService outbox = new com.shiftscheduling.scheduling.outbox.NotificationOutboxService();
        EmployeeDirectoryClient dir = new EmployeeDirectoryClient();
        dir.directory.put(1L, new RemoteEmployee(1L, 101L, "Asha", 3L, "Customer Support", true));
        dir.directory.put(2L, new RemoteEmployee(2L, 102L, "Bilal", 3L, "Customer Support", true));
        dir.directory.put(3L, new RemoteEmployee(3L, 103L, "Chen", 3L, "Customer Support", true));
        dir.directory.put(9L, new RemoteEmployee(9L, 109L, "Gone Gerry", 3L, "Customer Support", false));
        int[] saves = {0};

        Map<String, Function<Object[], Object>> h = new HashMap<>();
        h.put("saveAndFlush", a -> { ShiftAssignment s = (ShiftAssignment) a[0]; setField(s, "id", ids[0]++); store.add(s); saves[0]++; return s; });
        h.put("findByShift_IdOrderByEmployeeNameAsc", a -> store.stream().filter(s -> s.getShift().getId().equals(a[0])).toList());
        h.put("findByEmployeeIdAndShift_Date", a -> store.stream().filter(s -> s.getEmployeeId().equals(a[0]) && s.getShift().getDate().equals(a[1])).toList());
        h.put("findByUserIdAndShift_DateBetweenOrderByShift_DateAscShift_StartTimeAsc", a -> store.stream()
                .filter(s -> s.getUserId().equals(a[0]) && !s.getShift().getDate().isBefore((LocalDate) a[1]) && !s.getShift().getDate().isAfter((LocalDate) a[2])).toList());
        h.put("deleteAll", a -> { for (Object o : (Iterable<?>) a[0]) store.remove(o); return null; });
        ShiftAssignmentRepository repo = repo(ShiftAssignmentRepository.class, h);
        ShiftAssignmentService svc = new ShiftAssignmentService(repo, dir, outbox);

        Shift shift = new Shift();
        setField(shift, "id", 7L);
        shift.setName("Morning Desk"); shift.setDepartmentId(3L);
        shift.setDate(LocalDate.of(2027, 2, 10));
        shift.setStartTime(LocalTime.of(8, 0)); shift.setEndTime(LocalTime.of(16, 0));
        shift.setMaxStaff(3); shift.setMinStaff(1);
        RequestUser hrUser = new RequestUser(20L, "HR", "Priya HR");

        // HR creates a shift and assigns 2 employees
        List<ShiftAssignment> created = svc.assignNew(shift, List.of(1L, 2L), hrUser);
        check("shift: HR assigns two employees -> two assignments persisted", created.size() == 2 && store.size() == 2);
        List<NotificationPayload> sent = payloads(outbox);
        check("shift: exactly one notification per assigned employee", sent.size() == 2);
        check("shift: notifications go to the assigned users (101, 102)",
                sent.stream().map(NotificationPayload::userId).toList().equals(List.of(101L, 102L)));
        check("shift: notification title and type are correct",
                sent.stream().allMatch(p -> "New Shift Assigned".equals(p.title()) && "SHIFT_ASSIGNED".equals(p.type())));
        check("shift: notification text has shift name, date, times and department",
                sent.get(0).message().contains("Morning Desk") && sent.get(0).message().contains("10 Feb 2027")
                        && sent.get(0).message().contains("08:00 to 16:00") && sent.get(0).message().contains("Customer Support")
                        && sent.get(0).message().contains("Priya HR"));
        check("shift: unassigned employee (103) receives nothing",
                sent.stream().noneMatch(p -> p.userId() == 103L));
        check("shift: each notification has a per-assignment dedup key",
                sent.get(0).dedupKey().startsWith("SHIFT_ASSIGNED:") && !sent.get(0).dedupKey().equals(sent.get(1).dedupKey()));

        // Assigned employee sees the shift in their own schedule
        List<ShiftAssignmentResponse> mine = svc.forUser(101L, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 28));
        check("schedule: assigned employee (101) sees the shift in 'my shifts'",
                mine.size() == 1 && "Morning Desk".equals(mine.get(0).shiftName()));
        check("schedule: unassigned employee (103) sees no shift", svc.forUser(103L, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 28)).isEmpty());

        // Duplicate selections in one request
        int beforeDup = sent.size();
        Shift shift2 = new Shift(); setField(shift2, "id", 8L);
        shift2.setName("Evening Desk"); shift2.setDepartmentId(3L); shift2.setDate(LocalDate.of(2027, 2, 11));
        shift2.setStartTime(LocalTime.of(16, 0)); shift2.setEndTime(LocalTime.of(22, 0)); shift2.setMaxStaff(3); shift2.setMinStaff(1);
        svc.assignNew(shift2, List.of(3L, 3L, 3L), hrUser);
        check("shift: duplicate employee in one request -> one assignment and one notification",
                payloads(outbox).size() == beforeDup + 1 && store.stream().filter(s -> s.getEmployeeId() == 3L).count() == 1);

        // Inactive employee: nothing saved, nothing sent
        Shift shift3 = new Shift(); setField(shift3, "id", 9L);
        shift3.setName("Night Desk"); shift3.setDepartmentId(3L); shift3.setDate(LocalDate.of(2027, 2, 12));
        shift3.setStartTime(LocalTime.of(22, 0)); shift3.setEndTime(LocalTime.of(6, 0)); shift3.setOvernight(true);
        shift3.setMaxStaff(3); shift3.setMinStaff(1);
        int savesBefore = saves[0], notifBefore = payloads(outbox).size();
        expectRefused("shift: inactive employee cannot be assigned (422)",
                () -> svc.assignNew(shift3, List.of(9L), hrUser), "UNPROCESSABLE_ENTITY", "inactive");
        check("shift: refused inactive assignment saved nothing and notified nobody",
                saves[0] == savesBefore && payloads(outbox).size() == notifBefore);

        // Capacity
        Shift small = new Shift(); setField(small, "id", 10L);
        small.setName("Tiny"); small.setDepartmentId(3L); small.setDate(LocalDate.of(2027, 2, 13));
        small.setStartTime(LocalTime.of(8, 0)); small.setEndTime(LocalTime.of(9, 0)); small.setMaxStaff(2); small.setMinStaff(1);
        expectRefused("shift: more employees than max staff is refused (422)",
                () -> svc.assignNew(small, List.of(1L, 2L, 3L), hrUser), "UNPROCESSABLE_ENTITY", "at most 2");

        // Double booking: employee 1 already on Morning Desk (08-16); a 10-14 shift the same day overlaps
        Shift clash = new Shift(); setField(clash, "id", 11L);
        clash.setName("Clash Desk"); clash.setDepartmentId(3L); clash.setDate(LocalDate.of(2027, 2, 10));
        clash.setStartTime(LocalTime.of(10, 0)); clash.setEndTime(LocalTime.of(14, 0)); clash.setMaxStaff(3); clash.setMinStaff(1);
        expectRefused("shift: overlapping shift for the same employee is refused (409)",
                () -> svc.assignNew(clash, List.of(1L), hrUser), "CONFLICT", "overlaps");

        // Edit: keep 1, drop 2, add 3 -> only 3 gets a new notification
        int before = payloads(outbox).size();
        svc.sync(shift, List.of(1L, 3L), hrUser);
        List<NotificationPayload> afterSync = payloads(outbox);
        check("shift edit: removed employee (102) loses the assignment",
                store.stream().noneMatch(s -> s.getUserId() == 102L && s.getShift().getId() == 7L));
        check("shift edit: only the newly added employee (103) is notified",
                afterSync.size() == before + 1 && afterSync.get(afterSync.size() - 1).userId() == 103L);
        check("shift edit: kept employee (101) is not notified again", afterSync.stream().filter(p -> p.userId() == 101L).count() == 1);
        check("shift: no duplicate assignment row for one employee and shift",
                store.stream().filter(s -> s.getShift().getId() == 7L).map(ShiftAssignment::getEmployeeId).distinct().count()
                        == store.stream().filter(s -> s.getShift().getId() == 7L).count());

        // --- End to end: the outbox delivers the payloads; delivering them twice (retry) must not duplicate ---
    }

    static List<NotificationPayload> payloads(com.shiftscheduling.scheduling.outbox.NotificationOutboxService outbox) {
        List<NotificationPayload> p = new ArrayList<>();
        for (Object[] e : outbox.enqueued) p.add((NotificationPayload) e[1]);
        return p;
    }


    // ------------------------------------------------------------------
    // 3. Notifications, using the real NotificationService
    // ------------------------------------------------------------------
    static void notificationWorkflows() {
        Map<Long, Notification> rows = new LinkedHashMap<>();
        Map<String, Notification> byKey = new HashMap<>();
        long[] ids = {1};
        int[] saves = {0};
        Map<String, Function<Object[], Object>> h = new HashMap<>();
        h.put("findByDedupKey", a -> Optional.ofNullable(byKey.get((String) a[0])));
        h.put("findById", a -> Optional.ofNullable(rows.get((Long) a[0])));
        h.put("save", a -> {
            Notification n = (Notification) a[0];
            saves[0]++;
            if (getField(n, "id") == null) setField(n, "id", ids[0]++);
            rows.put((Long) getField(n, "id"), n);
            if (n.getDedupKey() != null) byKey.put(n.getDedupKey(), n);
            return n;
        });
        h.put("findByUserIdOrderByCreatedAtDesc", a -> rows.values().stream().filter(n -> n.getUserId().equals(a[0])).toList());
        NotificationRepository repo = repo(NotificationRepository.class, h);
        NotificationService svc = new NotificationService(repo);

        // Deliver the shift-assignment payloads exactly as the outbox would post them, twice (retry).
        com.shiftscheduling.scheduling.outbox.NotificationOutboxService outbox = new com.shiftscheduling.scheduling.outbox.NotificationOutboxService();
        // Reconstruct the two shift payloads for employees 101/102 with the real builder used by ShiftAssignmentService.
        EmployeeDirectoryClient dir = new EmployeeDirectoryClient();
        dir.directory.put(1L, new RemoteEmployee(1L, 101L, "Asha", 3L, "Customer Support", true));
        dir.directory.put(2L, new RemoteEmployee(2L, 102L, "Bilal", 3L, "Customer Support", true));
        List<ShiftAssignment> stored = new ArrayList<>();
        Map<String, Function<Object[], Object>> sh = new HashMap<>();
        sh.put("saveAndFlush", a -> { ShiftAssignment s = (ShiftAssignment) a[0]; setField(s, "id", 500L + stored.size()); stored.add(s); return s; });
        sh.put("findByShift_IdOrderByEmployeeNameAsc", a -> List.of());
        sh.put("findByEmployeeIdAndShift_Date", a -> List.of());
        ShiftAssignmentService shifts = new ShiftAssignmentService(repo(ShiftAssignmentRepository.class, sh), dir, outbox);
        Shift shift = new Shift(); setField(shift, "id", 7L);
        shift.setName("Morning Desk"); shift.setDepartmentId(3L); shift.setDate(LocalDate.of(2027, 2, 10));
        shift.setStartTime(LocalTime.of(8, 0)); shift.setEndTime(LocalTime.of(16, 0)); shift.setMaxStaff(3); shift.setMinStaff(1);
        shifts.assignNew(shift, List.of(1L, 2L), new RequestUser(20L, "HR", "Priya HR"));

        for (NotificationPayload p : payloads(outbox)) {
            NotificationRequest req = new NotificationRequest(p.userId(), p.title(), p.message(),
                    NotificationType.valueOf(p.type()), p.referenceId(), p.dedupKey());
            svc.createNotification(req);
            svc.createNotification(req); // retried delivery
        }
        check("notify: each assigned employee has exactly one shift notification after a retried delivery",
                rows.values().stream().filter(n -> n.getUserId() == 101L).count() == 1
                        && rows.values().stream().filter(n -> n.getUserId() == 102L).count() == 1);
        check("notify: shift notification is typed SHIFT_ASSIGNED", rows.values().stream()
                .allMatch(n -> n.getNotificationType() == NotificationType.SHIFT_ASSIGNED));
        check("notify: an unassigned user has no shift notification",
                rows.values().stream().noneMatch(n -> n.getUserId() == 103L));

        // Leave decision: deliver the real LeaveDecisionNotification payload from the leave service, twice.
        com.shiftscheduling.leave.outbox.NotificationOutboxService leaveOutbox = new com.shiftscheduling.leave.outbox.NotificationOutboxService();
        LeaveDecisionNotification approved = new LeaveDecisionNotification(5L, 900L, true, null, "ANNUAL",
                LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 3), "Priya HR", LocalDateTime.of(2026, 10, 25, 9, 0), "LEAVE_DECISION:900:5");
        LeaveDecisionRequest asRequest = new LeaveDecisionRequest(approved.userId(), approved.leaveId(), approved.approved(),
                approved.reason(), approved.leaveType(), approved.startDate(), approved.endDate(), approved.approverName(),
                approved.decidedAt(), approved.dedupKey());
        NotificationResponse a1 = svc.notifyLeaveDecision(asRequest);
        NotificationResponse a2 = svc.notifyLeaveDecision(asRequest);
        check("notify: leave approval reaches the applicant (5) with the agreed wording",
                a1.userId() == 5L && "Leave Request Approved".equals(a1.title())
                        && a1.message().equals("Your leave request from 01 Mar 2027 to 03 Mar 2027 has been approved by Priya HR. Leave type: ANNUAL. Decision date: 25 Oct 2026."));
        check("notify: retried leave decision does not duplicate the notification", a1.id().equals(a2.id()) && rows.values().stream()
                .filter(n -> "LEAVE_DECISION:900:5".equals(n.getDedupKey())).count() == 1);

        LeaveDecisionRequest rejected = new LeaveDecisionRequest(20L, 901L, false, "Team is short that week", "ANNUAL",
                LocalDate.of(2027, 4, 1), LocalDate.of(2027, 4, 2), "Admin Two", LocalDateTime.of(2026, 10, 25, 9, 0), "LEAVE_DECISION:901:20");
        NotificationResponse r1 = svc.notifyLeaveDecision(rejected);
        check("notify: leave rejection reaches the applicant with the reason",
                r1.userId() == 20L && "Leave Request Rejected".equals(r1.title()) && r1.message().contains("Reason: Team is short that week"));

        // Ownership: only the recipient may mark read
        NotificationResponse mine = svc.notifyLeaveDecision(asRequest);
        expectRefused("notify: another user cannot mark someone else's notification read (403)",
                () -> svc.markAsRead(mine.id(), 6L), "FORBIDDEN", "own notifications");
        check("notify: owner marks their notification read", svc.markAsRead(mine.id(), 5L).isRead());

        // Shift payload field-level check: the notification-service must accept the scheduling payload shape
        check("notify: shift payload keeps referenceId = shift id", rows.values().stream().anyMatch(n -> n.getReferenceId() != null && n.getReferenceId() == 7L));
    }


    // ------------------------------------------------------------------
    // 4. Department rule and revalidation on edit, using the real ShiftAssignmentService
    // ------------------------------------------------------------------
    static void departmentAndRevalidationChecks() {
        List<ShiftAssignment> store = new ArrayList<>();
        long[] ids = {2000};
        com.shiftscheduling.scheduling.outbox.NotificationOutboxService outbox = new com.shiftscheduling.scheduling.outbox.NotificationOutboxService();
        EmployeeDirectoryClient dir = new EmployeeDirectoryClient();
        dir.directory.put(1L, new RemoteEmployee(1L, 101L, "Asha", 3L, "Customer Support", true));
        dir.directory.put(3L, new RemoteEmployee(3L, 103L, "Chen", 3L, "Customer Support", true));
        dir.directory.put(4L, new RemoteEmployee(4L, 104L, "Dana", 9L, "Finance", true));
        Map<String, Function<Object[], Object>> h = new HashMap<>();
        h.put("saveAndFlush", a -> { ShiftAssignment s = (ShiftAssignment) a[0]; setField(s, "id", ids[0]++); store.add(s); return s; });
        h.put("findByShift_IdOrderByEmployeeNameAsc", a -> store.stream().filter(s -> s.getShift().getId().equals(a[0])).toList());
        h.put("findByEmployeeIdAndShift_Date", a -> store.stream().filter(s -> s.getEmployeeId().equals(a[0]) && s.getShift().getDate().equals(a[1])).toList());
        h.put("deleteAll", a -> { for (Object o : (Iterable<?>) a[0]) store.remove(o); return null; });
        ShiftAssignmentService svc = new ShiftAssignmentService(repo(ShiftAssignmentRepository.class, h), dir, outbox);
        RequestUser hrUser = new RequestUser(20L, "HR", "Priya HR");

        Shift p = shiftOn(50L, 3L, LocalDate.of(2027, 5, 4), LocalTime.of(8, 0), LocalTime.of(12, 0), 3);
        Shift q = shiftOn(51L, 3L, LocalDate.of(2027, 5, 4), LocalTime.of(14, 0), LocalTime.of(18, 0), 3);

        expectRefused("department: employee from another department cannot be assigned (422)",
                () -> svc.assignNew(p, List.of(4L), hrUser), "UNPROCESSABLE_ENTITY", "department");
        check("department: refused cross-department assignment saved nothing", store.stream().noneMatch(a -> a.getEmployeeId() == 4L));

        svc.assignNew(p, List.of(3L), hrUser);
        svc.assignNew(q, List.of(3L), hrUser);
        check("department: same-department employee on two non-overlapping shifts is allowed", store.size() == 2);

        // Edit the second shift's times so it overlaps the first one, with no new employee list.
        q.setStartTime(LocalTime.of(10, 0)); q.setEndTime(LocalTime.of(16, 0));
        expectRefused("revalidation: editing times so an existing assignment overlaps is refused (409)",
                () -> svc.revalidateExisting(q), "CONFLICT", "overlaps");
        q.setStartTime(LocalTime.of(14, 0)); q.setEndTime(LocalTime.of(18, 0));
        check("revalidation: valid edit passes", refusal(() -> svc.revalidateExisting(q)) == null);

        // Change the department with no new list: the existing employee is from the old department.
        q.setDepartmentId(9L);
        expectRefused("revalidation: changing department without a new list is refused (422)",
                () -> svc.revalidateExisting(q), "UNPROCESSABLE_ENTITY", "department");
        q.setDepartmentId(3L);

        // Edit with a new list that keeps employee 3: kept employees are re-validated and NOT notified again.
        int notifBefore = outbox.enqueued.size();
        svc.sync(q, List.of(3L), hrUser);
        check("edit: kept employee is not notified again", outbox.enqueued.size() == notifBefore);
        check("edit: kept employee keeps exactly one assignment", store.stream().filter(a -> a.getShift().getId() == 51L).count() == 1);

        // Edit with a new list containing a cross-department employee is refused and changes nothing.
        int before = store.size();
        expectRefused("edit: list containing another-department employee is refused (422)",
                () -> svc.sync(q, List.of(3L, 4L), hrUser), "UNPROCESSABLE_ENTITY", "department");
        check("edit: refused sync changed no assignments", store.size() == before);
    }

    static Shift shiftOn(long id, long dept, LocalDate date, LocalTime start, LocalTime end, int maxStaff) {
        Shift s = new Shift();
        setField(s, "id", id);
        s.setName("Shift " + id); s.setDepartmentId(dept); s.setDate(date);
        s.setStartTime(start); s.setEndTime(end); s.setMaxStaff(maxStaff); s.setMinStaff(1);
        return s;
    }

    // ------------------------------------------------------------------
    // 5. Gateway role table (real AccessRules class)
    // ------------------------------------------------------------------
    static void accessRuleChecks() {
        String E = "EMPLOYEE", HR_ = "HR", AD = "ADMIN";
        check("gateway: employee cannot read schedules", !AccessRules.isAllowed("GET", "/api/schedules", E));
        check("gateway: employee cannot read one schedule", !AccessRules.isAllowed("GET", "/api/schedules/5", E));
        check("gateway: HR can read schedules", AccessRules.isAllowed("GET", "/api/schedules", HR_));
        check("gateway: employee can read the active employee list (attendance screens)", AccessRules.isAllowed("GET", "/api/employees/all", E));
        check("gateway: employee cannot read one employee record", !AccessRules.isAllowed("GET", "/api/employees/12", E));
        check("gateway: employee cannot create employees", !AccessRules.isAllowed("POST", "/api/employees", E));
        check("gateway: HR can read one employee record", AccessRules.isAllowed("GET", "/api/employees/12", HR_));
        check("gateway: employee can read their own employee lookup", AccessRules.isAllowed("GET", "/api/employees/user/5", E));
        check("gateway: employee can read departments and skills", AccessRules.isAllowed("GET", "/api/departments", E) && AccessRules.isAllowed("GET", "/api/skills", E));
        check("gateway: employee cannot create departments", !AccessRules.isAllowed("POST", "/api/departments", E));
        check("gateway: HR can create departments", AccessRules.isAllowed("POST", "/api/departments", HR_));
        check("gateway: employee cannot list shift definitions", !AccessRules.isAllowed("GET", "/api/shifts", E));
        check("gateway: employee can read their own assignments", AccessRules.isAllowed("GET", "/api/shifts/assignments/my", E));
        check("gateway: employee cannot read another shift's assignees", !AccessRules.isAllowed("GET", "/api/shifts/7/assignments", E));
        check("gateway: employee cannot edit shifts", !AccessRules.isAllowed("PUT", "/api/shifts/3", E));
        check("gateway: HR can create shifts", AccessRules.isAllowed("POST", "/api/shifts", HR_));
        check("gateway: nobody can create notifications from outside", !AccessRules.isAllowed("POST", "/api/notifications", AD));
        check("gateway: employee cannot list user accounts", !AccessRules.isAllowed("GET", "/api/auth/users", E));
        check("gateway: admin can list user accounts", AccessRules.isAllowed("GET", "/api/auth/users", AD));
        check("gateway: HR cannot deactivate accounts", !AccessRules.isAllowed("PATCH", "/api/auth/users/9/deactivate", HR_));
        check("gateway: HR may reach register (service narrows the role)", AccessRules.isAllowed("POST", "/api/auth/register", HR_));
        check("gateway: employee cannot register accounts", !AccessRules.isAllowed("POST", "/api/auth/register", E));
        check("gateway: company-wide approved leave is HR/ADMIN only", !AccessRules.isAllowed("GET", "/api/leaves/approved", E) && AccessRules.isAllowed("GET", "/api/leaves/approved", HR_));
        check("gateway: leave check is HR/ADMIN only", !AccessRules.isAllowed("GET", "/api/leaves/check", E));
        check("gateway: employees can apply for and view pending leave (service filters)", AccessRules.isAllowed("POST", "/api/leaves", E) && AccessRules.isAllowed("GET", "/api/leaves/pending", E));
        check("gateway: attendance record by id is manager-only", !AccessRules.isAllowed("GET", "/api/attendance/12", E) && AccessRules.isAllowed("GET", "/api/attendance/12", HR_));
        check("gateway: attendance correction is manager-only", !AccessRules.isAllowed("POST", "/api/attendance/12/correct", E));
        check("gateway: attendance CSV export is manager-only", !AccessRules.isAllowed("GET", "/api/attendance/export/csv", E));
        check("gateway: attendance overview is manager-only", !AccessRules.isAllowed("GET", "/api/attendance/overview", E));
        check("gateway: employees can check in and read history (service self-checks)", AccessRules.isAllowed("POST", "/api/attendance/check-in", E) && AccessRules.isAllowed("GET", "/api/attendance/history", E));
        check("gateway: employees can read today's attendance (attendance screen)", AccessRules.isAllowed("GET", "/api/attendance/date/2026-10-25", E));
        check("gateway: unknown role gets nothing from manager rules", !AccessRules.isAllowed("GET", "/api/schedules", "GUEST"));
        check("gateway: prefix match does not leak into lookalike paths", AccessRules.isAllowed("GET", "/api/employeesX", E));
    }
}
