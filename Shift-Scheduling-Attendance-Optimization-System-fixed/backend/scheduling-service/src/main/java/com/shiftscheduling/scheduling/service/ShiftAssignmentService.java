package com.shiftscheduling.scheduling.service;

import com.shiftscheduling.scheduling.client.EmployeeDirectoryClient;
import com.shiftscheduling.scheduling.client.RemoteEmployee;
import com.shiftscheduling.scheduling.dto.NotificationPayload;
import com.shiftscheduling.scheduling.dto.ShiftAssignmentResponse;
import com.shiftscheduling.scheduling.entity.Shift;
import com.shiftscheduling.scheduling.entity.ShiftAssignment;
import com.shiftscheduling.scheduling.exception.ApiException;
import com.shiftscheduling.scheduling.outbox.NotificationOutboxService;
import com.shiftscheduling.scheduling.repository.ShiftAssignmentRepository;
import com.shiftscheduling.scheduling.security.RequestUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Shift -> employee assignments.
 *
 * Rules enforced here (server side, whatever the UI does):
 *  - each employee at most once per shift (DB unique key + explicit check)
 *  - the employee must exist and be active in employee-service
 *  - no overlapping shift for the same employee on the same date
 *  - the employee belongs to the shift's department
 *  - no more employees than the shift's maxStaff
 *  - exactly one "New Shift Assigned" notification per NEW assignment, queued in the same transaction
 */
@Service
public class ShiftAssignmentService {

    static final String NOTIFICATION_PATH = "/api/notifications";
    static final String NOTIFICATION_TYPE = "SHIFT_ASSIGNED";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final ShiftAssignmentRepository assignments;
    private final EmployeeDirectoryClient employees;
    private final NotificationOutboxService outbox;

    public ShiftAssignmentService(ShiftAssignmentRepository assignments,
                                  EmployeeDirectoryClient employees,
                                  NotificationOutboxService outbox) {
        this.assignments = assignments;
        this.employees = employees;
        this.outbox = outbox;
    }

    /** Assignments for a shift that has just been created. */
    @Transactional
    public List<ShiftAssignment> assignNew(Shift shift, List<Long> employeeIds, RequestUser actor) {
        List<Long> ids = distinct(employeeIds);
        if (ids.isEmpty()) {
            return List.of();
        }
        checkCapacity(shift, ids.size());
        List<RemoteEmployee> resolved = resolveActive(shift, ids);
        String departmentName = employees.departmentName(shift.getDepartmentId());

        List<ShiftAssignment> created = new ArrayList<>();
        for (RemoteEmployee employee : resolved) {
            created.add(create(shift, employee, actor, departmentName));
        }
        return created;
    }

    /**
     * Brings an existing shift to exactly the given employees. Every employee in the submitted set is
     * re-validated (active, same department, no overlap with other shifts). Removed employees lose their
     * assignment silently; only newly added employees are notified.
     */
    @Transactional
    public void sync(Shift shift, List<Long> employeeIds, RequestUser actor) {
        List<Long> wanted = distinct(employeeIds);
        checkCapacity(shift, wanted.size());

        Set<Long> wantedSet = new HashSet<>(wanted);
        List<ShiftAssignment> current = assignments.findByShift_IdOrderByEmployeeNameAsc(shift.getId());
        List<ShiftAssignment> toRemove = current.stream()
                .filter(a -> !wantedSet.contains(a.getEmployeeId())).toList();
        Set<Long> keep = current.stream().map(ShiftAssignment::getEmployeeId)
                .filter(wantedSet::contains).collect(Collectors.toSet());

        List<RemoteEmployee> resolved = resolveActive(shift, wanted);   // validates everyone submitted
        if (!toRemove.isEmpty()) {
            assignments.deleteAll(toRemove);
        }
        String departmentName = null;
        for (RemoteEmployee employee : resolved) {
            if (keep.contains(employee.id())) {
                assertNoOverlap(shift, employee);                       // kept: still valid after an edit?
            } else {
                if (departmentName == null) departmentName = employees.departmentName(shift.getDepartmentId());
                create(shift, employee, actor, departmentName);         // new: assign and notify
            }
        }
    }

    /**
     * Called when a shift is edited without a new employee list. Existing assignments must still be
     * valid for the new date, times and department; otherwise the edit is refused and nothing changes.
     */
    @Transactional
    public void revalidateExisting(Shift shift) {
        List<ShiftAssignment> current = assignments.findByShift_IdOrderByEmployeeNameAsc(shift.getId());
        if (current.isEmpty()) {
            return;
        }
        List<Long> ids = current.stream().map(ShiftAssignment::getEmployeeId).distinct().toList();
        checkCapacity(shift, ids.size());
        for (RemoteEmployee employee : resolveActive(shift, ids)) {
            assertNoOverlap(shift, employee);
        }
    }

    @Transactional(readOnly = true)
    public List<ShiftAssignmentResponse> forShift(Long shiftId) {
        return assignments.findByShift_IdOrderByEmployeeNameAsc(shiftId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ShiftAssignmentResponse> forUser(Long userId, LocalDate start, LocalDate end) {
        return assignments.findByUserIdAndShift_DateBetweenOrderByShift_DateAscShift_StartTimeAsc(userId, start, end)
                .stream().map(this::toResponse).toList();
    }

    // ---------- internals ----------

    private ShiftAssignment create(Shift shift, RemoteEmployee employee, RequestUser actor, String departmentName) {
        assertNoOverlap(shift, employee);

        ShiftAssignment assignment = new ShiftAssignment();
        assignment.setShift(shift);
        assignment.setEmployeeId(employee.id());
        assignment.setUserId(employee.userId());
        assignment.setEmployeeName(employee.fullName());
        assignment.setAssignedBy(actor.userId());
        try {
            assignment = assignments.saveAndFlush(assignment);
        } catch (DataIntegrityViolationException race) {
            throw new ApiException(HttpStatus.CONFLICT, employee.fullName() + " is already assigned to this shift");
        }

        // Same transaction as the assignment: recorded if and only if the assignment is.
        // dedupKey is per assignment row, so a retried delivery never notifies twice.
        outbox.enqueue(NOTIFICATION_PATH, new NotificationPayload(
                employee.userId(),
                "New Shift Assigned",
                buildMessage(shift, departmentName, actor),
                NOTIFICATION_TYPE,
                shift.getId(),
                "SHIFT_ASSIGNED:" + assignment.getId()));
        return assignment;
    }

    private void assertNoOverlap(Shift shift, RemoteEmployee employee) {
        for (ShiftAssignment other : assignments.findByEmployeeIdAndShift_Date(employee.id(), shift.getDate())) {
            Shift o = other.getShift();
            if (o.getId().equals(shift.getId())) continue;
            if (overlaps(shift, o)) {
                throw new ApiException(HttpStatus.CONFLICT, employee.fullName() + " is already assigned to '"
                        + o.getName() + "' on " + shift.getDate() + ", which overlaps this shift");
            }
        }
    }

    /** Resolves employees and enforces: active, and belonging to the shift's department. */
    private List<RemoteEmployee> resolveActive(Shift shift, List<Long> ids) {
        List<RemoteEmployee> result = new ArrayList<>();
        for (Long id : ids) {
            RemoteEmployee e = employees.get(id);
            if (!e.active()) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        e.fullName() + " is inactive and cannot be assigned to shifts");
            }
            if (!Objects.equals(e.departmentId(), shift.getDepartmentId())) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, e.fullName()
                        + " is not in this shift's department. Only employees of the shift's department can be assigned.");
            }
            result.add(e);
        }
        return result;
    }

    private void checkCapacity(Shift shift, int selected) {
        if (selected > shift.getMaxStaff()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Too many employees: '" + shift.getName()
                    + "' allows at most " + shift.getMaxStaff() + " staff, but " + selected + " were selected");
        }
    }

    static boolean overlaps(Shift a, Shift b) {
        if (!a.isOvernight() && !b.isOvernight()) {
            return a.getStartTime().isBefore(b.getEndTime()) && b.getStartTime().isBefore(a.getEndTime());
        }
        return true; // an overnight shift can run into the same day's other shifts; be conservative
    }

    private static String buildMessage(Shift shift, String departmentName, RequestUser actor) {
        String dept = departmentName != null ? departmentName : "Department #" + shift.getDepartmentId();
        String end = shift.getEndTime().format(TIME) + (shift.isOvernight() ? " (next day)" : "");
        return "You have been assigned to shift '" + shift.getName() + "' on " + shift.getDate().format(DATE)
                + " from " + shift.getStartTime().format(TIME) + " to " + end
                + ". Department: " + dept + ". Assigned by " + actor.displayName() + ".";
    }

    private static List<Long> distinct(List<Long> ids) {
        if (ids == null) return List.of();
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }

    private ShiftAssignmentResponse toResponse(ShiftAssignment a) {
        Shift s = a.getShift();
        return new ShiftAssignmentResponse(a.getId(), s.getId(), s.getName(), s.getDate(),
                s.getStartTime(), s.getEndTime(), s.getDepartmentId(), a.getEmployeeId(),
                a.getEmployeeName(), a.getUserId(), a.getStatus(), a.getAssignedAt());
    }
}
