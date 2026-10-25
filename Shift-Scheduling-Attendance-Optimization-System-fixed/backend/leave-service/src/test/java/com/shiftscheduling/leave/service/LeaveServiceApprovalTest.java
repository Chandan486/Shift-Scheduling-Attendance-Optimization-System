package com.shiftscheduling.leave.service;

import com.shiftscheduling.leave.dto.LeaveApprovalRequest;
import com.shiftscheduling.leave.dto.LeaveDecisionNotification;
import com.shiftscheduling.leave.dto.LeaveRequestDto;
import com.shiftscheduling.leave.dto.LeaveResponse;
import com.shiftscheduling.leave.entity.LeaveBalance;
import com.shiftscheduling.leave.entity.LeaveRequest;
import com.shiftscheduling.leave.entity.LeaveStatus;
import com.shiftscheduling.leave.entity.LeaveType;
import com.shiftscheduling.leave.exception.ApiException;
import com.shiftscheduling.leave.outbox.NotificationOutboxService;
import com.shiftscheduling.leave.repository.LeaveBalanceRepository;
import com.shiftscheduling.leave.repository.LeaveRequestRepository;
import com.shiftscheduling.leave.security.RequestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaveServiceApprovalTest {

    @Mock LeaveRequestRepository leaveRepo;
    @Mock LeaveBalanceRepository balanceRepo;
    @Mock NotificationOutboxService outbox;

    LeaveService service;

    static final RequestUser HR = new RequestUser(20L, "HR", "Priya HR");
    static final RequestUser ADMIN_A = new RequestUser(10L, "ADMIN", "Admin One");
    static final RequestUser ADMIN_B = new RequestUser(11L, "ADMIN", "Admin Two");

    @BeforeEach
    void setUp() {
        service = new LeaveService(leaveRepo, balanceRepo, outbox);
        // save() echoes the entity back, like JPA does
        lenient().when(leaveRepo.save(any(LeaveRequest.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(balanceRepo.save(any(LeaveBalance.class))).thenAnswer(i -> i.getArgument(0));
    }

    private LeaveRequest pending(long applicantUserId, String applicantRole, LeaveType type, int days) {
        LeaveRequest r = new LeaveRequest();
        setId(r, 100L + applicantUserId);
        r.setEmployeeId(applicantUserId);
        r.setApplicantUserId(applicantUserId);
        r.setApplicantRole(applicantRole);
        r.setApplicantName("Applicant " + applicantUserId);
        r.setLeaveType(type);
        r.setStartDate(LocalDate.of(2027, 3, 1));
        r.setEndDate(LocalDate.of(2027, 3, days));
        r.setReason("Family event, need time off");
        r.setStatus(LeaveStatus.PENDING);
        return r;
    }

    private static void setId(LeaveRequest r, Long id) {
        try {
            var f = LeaveRequest.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(r, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private LeaveBalance balance(long employeeId, int annualRemaining) {
        LeaveBalance b = new LeaveBalance();
        b.setEmployeeId(employeeId);
        b.setYear(2027);
        b.setAnnualLeave(20);
        b.setAnnualUsed(20 - annualRemaining);
        b.setSickLeave(10);
        b.setPersonalLeave(5);
        return b;
    }

    private void givenPending(LeaveRequest r) {
        when(leaveRepo.findByIdForUpdate(r.getId())).thenReturn(Optional.of(r));
    }

    // ---- approval matrix ----

    @Test
    void employee_leave_is_approved_by_hr_and_deducts_balance_and_notifies_applicant() {
        LeaveRequest r = pending(5L, "EMPLOYEE", LeaveType.ANNUAL, 3);
        givenPending(r);
        when(balanceRepo.findByEmployeeIdAndYearForUpdate(5L, 2027)).thenReturn(Optional.of(balance(5L, 10)));

        LeaveResponse out = service.decide(r.getId(), new LeaveApprovalRequest(LeaveStatus.APPROVED, null), HR);

        assertEquals(LeaveStatus.APPROVED, out.status());
        assertEquals(20L, out.reviewedBy());
        assertNotNull(out.reviewedAt());
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outbox).enqueue(eq("/api/notifications/leave-decision"), payload.capture());
        LeaveDecisionNotification n = (LeaveDecisionNotification) payload.getValue();
        assertEquals(5L, n.userId(), "notification goes to the applicant, not the approver");
        assertTrue(n.approved());
        assertEquals("Priya HR", n.approverName());
    }

    @Test
    void hr_leave_is_approved_by_admin() {
        LeaveRequest r = pending(20L, "HR", LeaveType.SICK, 1);
        givenPending(r);
        when(balanceRepo.findByEmployeeIdAndYearForUpdate(20L, 2027)).thenReturn(Optional.of(balance(20L, 10)));

        LeaveResponse out = service.decide(r.getId(), new LeaveApprovalRequest(LeaveStatus.APPROVED, null), ADMIN_A);
        assertEquals(LeaveStatus.APPROVED, out.status());
    }

    @Test
    void hr_cannot_approve_their_own_leave() {
        LeaveRequest r = pending(20L, "HR", LeaveType.ANNUAL, 2);
        givenPending(r);
        RequestUser self = new RequestUser(20L, "ADMIN", "Same Person");

        ApiException ex = assertThrows(ApiException.class, () ->
                service.decide(r.getId(), new LeaveApprovalRequest(LeaveStatus.APPROVED, null), self));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertEquals(LeaveStatus.PENDING, r.getStatus());
        verifyNoInteractions(outbox);
    }

    @Test
    void hr_cannot_decide_hr_leave() {
        LeaveRequest r = pending(20L, "HR", LeaveType.ANNUAL, 2);
        givenPending(r);
        RequestUser otherHr = new RequestUser(21L, "HR", "Other HR");
        ApiException ex = assertThrows(ApiException.class, () ->
                service.decide(r.getId(), new LeaveApprovalRequest(LeaveStatus.APPROVED, null), otherHr));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertTrue(ex.getMessage().contains("Only ADMIN"));
    }

    @Test
    void admin_leave_needs_a_different_admin() {
        LeaveRequest r = pending(10L, "ADMIN", LeaveType.PERSONAL, 1);
        givenPending(r);
        RequestUser self = ADMIN_A;

        ApiException ex = assertThrows(ApiException.class, () ->
                service.decide(r.getId(), new LeaveApprovalRequest(LeaveStatus.REJECTED, "Please reschedule"), self));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertTrue(ex.getMessage().contains("different authorised ADMIN"));
        assertEquals(LeaveStatus.PENDING, r.getStatus());
    }

    @Test
    void admin_leave_is_decided_by_another_admin() {
        LeaveRequest r = pending(10L, "ADMIN", LeaveType.PERSONAL, 1);
        givenPending(r);
        when(balanceRepo.findByEmployeeIdAndYearForUpdate(10L, 2027)).thenReturn(Optional.of(balance(10L, 10)));

        LeaveResponse out = service.decide(r.getId(), new LeaveApprovalRequest(LeaveStatus.APPROVED, null), ADMIN_B);
        assertEquals(LeaveStatus.APPROVED, out.status());
        assertEquals(11L, out.reviewedBy());
    }

    @Test
    void employee_leave_cannot_be_decided_by_admin() {
        LeaveRequest r = pending(5L, "EMPLOYEE", LeaveType.ANNUAL, 1);
        givenPending(r);
        ApiException ex = assertThrows(ApiException.class, () ->
                service.decide(r.getId(), new LeaveApprovalRequest(LeaveStatus.APPROVED, null), ADMIN_A));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertTrue(ex.getMessage().contains("Only HR"));
    }

    @Test
    void unknown_applicant_role_requires_admin_not_hr() {
        LeaveRequest legacy = pending(5L, null, LeaveType.UNPAID, 1);
        givenPending(legacy);
        ApiException ex = assertThrows(ApiException.class, () ->
                service.decide(legacy.getId(), new LeaveApprovalRequest(LeaveStatus.APPROVED, null), HR));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
    }

    // ---- decisions ----

    @Test
    void rejection_requires_a_reason() {
        LeaveRequest r = pending(5L, "EMPLOYEE", LeaveType.ANNUAL, 1);
        givenPending(r);
        ApiException ex = assertThrows(ApiException.class, () ->
                service.decide(r.getId(), new LeaveApprovalRequest(LeaveStatus.REJECTED, "   "), HR));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertEquals(LeaveStatus.PENDING, r.getStatus());
    }

    @Test
    void rejection_stores_reason_and_notifies_with_it() {
        LeaveRequest r = pending(5L, "EMPLOYEE", LeaveType.ANNUAL, 1);
        givenPending(r);

        LeaveResponse out = service.decide(r.getId(), new LeaveApprovalRequest(LeaveStatus.REJECTED, "Team is short that week"), HR);

        assertEquals(LeaveStatus.REJECTED, out.status());
        assertEquals("Team is short that week", out.reviewNotes());
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(outbox).enqueue(anyString(), payload.capture());
        LeaveDecisionNotification n = (LeaveDecisionNotification) payload.getValue();
        assertFalse(n.approved());
        assertEquals("Team is short that week", n.reason());
        verify(balanceRepo, never()).save(any());
    }

    @Test
    void already_decided_request_is_rejected_with_conflict_and_no_second_deduction() {
        LeaveRequest r = pending(5L, "EMPLOYEE", LeaveType.ANNUAL, 2);
        r.setStatus(LeaveStatus.APPROVED);
        givenPending(r);

        ApiException ex = assertThrows(ApiException.class, () ->
                service.decide(r.getId(), new LeaveApprovalRequest(LeaveStatus.APPROVED, null), HR));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verifyNoInteractions(balanceRepo);
        verifyNoInteractions(outbox);
    }

    @Test
    void approval_fails_cleanly_when_balance_no_longer_covers_the_leave() {
        LeaveRequest r = pending(5L, "EMPLOYEE", LeaveType.ANNUAL, 5);
        givenPending(r);
        when(balanceRepo.findByEmployeeIdAndYearForUpdate(5L, 2027)).thenReturn(Optional.of(balance(5L, 2)));

        ApiException ex = assertThrows(ApiException.class, () ->
                service.decide(r.getId(), new LeaveApprovalRequest(LeaveStatus.APPROVED, null), HR));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals(LeaveStatus.PENDING, r.getStatus());
        verifyNoInteractions(outbox);
    }

    @Test
    void unknown_leave_is_not_found() {
        when(leaveRepo.findByIdForUpdate(999L)).thenReturn(Optional.empty());
        ApiException ex = assertThrows(ApiException.class, () ->
                service.decide(999L, new LeaveApprovalRequest(LeaveStatus.APPROVED, null), HR));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    // ---- queues and applications ----

    @Test
    void hr_queue_contains_only_employee_requests_they_can_decide() {
        LeaveRequest emp = pending(5L, "EMPLOYEE", LeaveType.ANNUAL, 1);
        LeaveRequest hrOwn = pending(20L, "HR", LeaveType.ANNUAL, 1);
        when(leaveRepo.findByStatus(LeaveStatus.PENDING)).thenReturn(List.of(emp, hrOwn));

        List<LeaveResponse> queue = service.getPendingLeavesFor(HR);
        assertEquals(1, queue.size());
        assertEquals(5L, queue.get(0).applicantUserId());
    }

    @Test
    void employee_callers_get_an_empty_queue() {
        assertTrue(service.getPendingLeavesFor(new RequestUser(5L, "EMPLOYEE", "x")).isEmpty());
        verifyNoInteractions(leaveRepo);
    }

    @Test
    void applicant_cannot_submit_leave_for_someone_else() {
        LeaveRequestDto dto = new LeaveRequestDto(7L, LeaveType.ANNUAL,
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(11), "Wedding in the family");
        ApiException ex = assertThrows(ApiException.class, () ->
                service.applyLeave(dto, new RequestUser(5L, "EMPLOYEE", "Me")));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        verify(leaveRepo, never()).save(any());
    }

    @Test
    void overlapping_pending_or_approved_leave_is_refused() {
        LeaveRequestDto dto = new LeaveRequestDto(5L, LeaveType.ANNUAL,
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(12), "Wedding in the family");
        when(leaveRepo.findByEmployeeIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                anyLong(), any(), any(), any())).thenReturn(List.of(pending(5L, "EMPLOYEE", LeaveType.ANNUAL, 1)));

        ApiException ex = assertThrows(ApiException.class, () ->
                service.applyLeave(dto, new RequestUser(5L, "EMPLOYEE", "Me")));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        verify(leaveRepo, never()).save(any());
    }

    @Test
    void new_application_is_pending_and_records_applicant_identity_from_the_token() {
        LeaveRequestDto dto = new LeaveRequestDto(5L, LeaveType.UNPAID,
                LocalDate.now().plusDays(10), LocalDate.now().plusDays(10), "Personal appointment abroad");
        when(leaveRepo.findByEmployeeIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                anyLong(), any(), any(), any())).thenReturn(List.of());

        LeaveResponse out = service.applyLeave(dto, new RequestUser(5L, "EMPLOYEE", "Ravi Kumar"));

        assertEquals(LeaveStatus.PENDING, out.status());
        assertEquals(5L, out.applicantUserId());
        assertEquals("EMPLOYEE", out.applicantRole());
        assertEquals("Ravi Kumar", out.applicantName());
    }

    private static String anyString() { return any(String.class); }
}
