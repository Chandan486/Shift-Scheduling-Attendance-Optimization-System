package com.shiftscheduling.leave.service;

import com.shiftscheduling.leave.dto.*;
import com.shiftscheduling.leave.entity.*;
import com.shiftscheduling.leave.exception.ApiException;
import com.shiftscheduling.leave.outbox.NotificationOutboxService;
import com.shiftscheduling.leave.repository.LeaveBalanceRepository;
import com.shiftscheduling.leave.repository.LeaveRequestRepository;
import com.shiftscheduling.leave.security.RequestUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Leave workflow and approval rules.
 *
 *   applicant EMPLOYEE -> decided by HR
 *   applicant HR       -> decided by ADMIN (never by themselves)
 *   applicant ADMIN    -> decided by a different ADMIN (never by themselves)
 *
 * Identity always comes from the gateway-verified caller (RequestUser), never from the body.
 */
@Service
public class LeaveService {

    static final String DECISION_PATH = "/api/notifications/leave-decision";

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final NotificationOutboxService outbox;

    public LeaveService(LeaveRequestRepository leaveRequestRepository,
                        LeaveBalanceRepository leaveBalanceRepository,
                        NotificationOutboxService outbox) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.outbox = outbox;
    }

    @Transactional
    public LeaveResponse applyLeave(LeaveRequestDto dto, RequestUser applicant) {
        if (!applicant.userId().equals(dto.employeeId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only submit leave requests for yourself");
        }
        if (dto.endDate().isBefore(dto.startDate())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "End date cannot be before start date");
        }
        if (dto.startDate().isBefore(LocalDate.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Start date cannot be in the past");
        }

        List<LeaveRequest> overlapping = leaveRequestRepository
                .findByEmployeeIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        dto.employeeId(), List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED),
                        dto.endDate(), dto.startDate());
        if (!overlapping.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "You already have a pending or approved leave request that overlaps these dates");
        }

        int days = (int) ChronoUnit.DAYS.between(dto.startDate(), dto.endDate()) + 1;

        if (dto.leaveType() != LeaveType.UNPAID) {
            LeaveBalance balance = leaveBalanceRepository
                    .findByEmployeeIdAndYear(dto.employeeId(), dto.startDate().getYear())
                    .orElseGet(() -> initializeBalance(dto.employeeId(), dto.startDate().getYear()));

            int remaining = getRemainingForType(balance, dto.leaveType());
            if (days > remaining) {
                throw new ApiException(HttpStatus.CONFLICT, "Insufficient " + dto.leaveType()
                        + " leave balance. Remaining: " + remaining + ", Requested: " + days);
            }
        }

        LeaveRequest request = new LeaveRequest();
        request.setEmployeeId(dto.employeeId());
        request.setApplicantUserId(applicant.userId());
        request.setApplicantRole(normaliseRole(applicant.role()));
        request.setApplicantName(applicant.name());
        request.setLeaveType(dto.leaveType());
        request.setStartDate(dto.startDate());
        request.setEndDate(dto.endDate());
        request.setReason(dto.reason());
        request.setStatus(LeaveStatus.PENDING);

        return toResponse(leaveRequestRepository.save(request));
    }

    /**
     * Approve or reject a pending request. The row is locked for the whole decision, so a repeated
     * or concurrent decision gets a 409 and never deducts balance twice. The applicant's notification
     * is written to the outbox in this same transaction.
     */
    @Transactional
    public LeaveResponse decide(Long leaveId, LeaveApprovalRequest decision, RequestUser reviewer) {
        if (decision.status() != LeaveStatus.APPROVED && decision.status() != LeaveStatus.REJECTED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Decision must be APPROVED or REJECTED");
        }

        LeaveRequest request = leaveRequestRepository.findByIdForUpdate(leaveId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Leave request not found with id: " + leaveId));

        if (request.getStatus() != LeaveStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Leave request has already been " + request.getStatus().name().toLowerCase());
        }

        String denial = denialReason(request, reviewer);
        if (denial != null) {
            throw new ApiException(HttpStatus.FORBIDDEN, denial);
        }

        String notes = trimToNull(decision.reviewNotes());
        if (decision.status() == LeaveStatus.REJECTED && notes == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A rejection reason is required");
        }

        if (decision.status() == LeaveStatus.APPROVED && request.getLeaveType() != LeaveType.UNPAID) {
            int days = (int) ChronoUnit.DAYS.between(request.getStartDate(), request.getEndDate()) + 1;
            int year = request.getStartDate().getYear();
            LeaveBalance balance = leaveBalanceRepository
                    .findByEmployeeIdAndYearForUpdate(request.getEmployeeId(), year)
                    .orElseGet(() -> initializeBalance(request.getEmployeeId(), year));

            int remaining = getRemainingForType(balance, request.getLeaveType());
            if (days > remaining) {
                throw new ApiException(HttpStatus.CONFLICT, "Cannot approve: applicant has only "
                        + remaining + " " + request.getLeaveType() + " day(s) left, requested " + days);
            }
            deductBalance(balance, request.getLeaveType(), days);
            leaveBalanceRepository.save(balance);
        }

        LocalDateTime decidedAt = LocalDateTime.now();
        request.setStatus(decision.status());
        request.setReviewedBy(reviewer.userId());
        request.setReviewedAt(decidedAt);
        request.setReviewNotes(notes);
        LeaveRequest saved = leaveRequestRepository.save(request);

        boolean approved = saved.getStatus() == LeaveStatus.APPROVED;
        outbox.enqueue(DECISION_PATH, new LeaveDecisionNotification(
                applicantUserIdOf(saved),
                saved.getId(),
                approved,
                approved ? null : notes,
                saved.getLeaveType().name(),
                saved.getStartDate(),
                saved.getEndDate(),
                reviewer.name(),
                decidedAt,
                "LEAVE_DECISION:" + saved.getId() + ":" + applicantUserIdOf(saved)));

        return toResponse(saved);
    }

    /**
     * Returns null when the reviewer may decide this request, otherwise the reason they may not.
     * This is the single place where the approval rules are written down.
     */
    String denialReason(LeaveRequest request, RequestUser reviewer) {
        Long applicant = applicantUserIdOf(request);
        String applicantRole = normaliseRole(request.getApplicantRole());

        if (reviewer.userId() != null && reviewer.userId().equals(applicant)) {
            if ("ADMIN".equals(applicantRole)) {
                return "You cannot approve or reject your own leave request. "
                        + "Admin leave must be decided by a different authorised ADMIN.";
            }
            return "You cannot approve or reject your own leave request";
        }

        String required = requiredApproverRole(applicantRole);
        if (!required.equalsIgnoreCase(reviewer.role())) {
            return "Only " + required + " users can approve or reject this leave request";
        }
        return null;
    }

    /** Pending requests this caller is allowed to act on (HR sees employee requests; ADMIN sees HR/ADMIN requests). */
    public List<LeaveResponse> getPendingLeavesFor(RequestUser reviewer) {
        if (!reviewer.isAdminOrHr()) {
            return List.of();
        }
        return leaveRequestRepository.findByStatus(LeaveStatus.PENDING).stream()
                .filter(r -> denialReason(r, reviewer) == null)
                .sorted(java.util.Comparator.comparing(LeaveRequest::getCreatedAt,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .map(this::toResponse)
                .toList();
    }

    public Page<LeaveResponse> getLeavesByEmployee(Long employeeId, Pageable pageable) {
        return leaveRequestRepository.findByEmployeeId(employeeId, pageable).map(this::toResponse);
    }

    public List<LeaveResponse> getApprovedLeaves(LocalDate startDate, LocalDate endDate) {
        return leaveRequestRepository
                .findByStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        LeaveStatus.APPROVED, endDate, startDate)
                .stream().map(this::toResponse).toList();
    }

    public LeaveBalanceResponse getLeaveBalance(Long employeeId, int year) {
        LeaveBalance balance = leaveBalanceRepository.findByEmployeeIdAndYear(employeeId, year)
                .orElseGet(() -> initializeBalance(employeeId, year));
        return toBalanceResponse(balance);
    }

    @Transactional
    public LeaveBalance initializeBalance(Long employeeId, int year) {
        return leaveBalanceRepository.findByEmployeeIdAndYear(employeeId, year)
                .orElseGet(() -> {
                    LeaveBalance balance = new LeaveBalance();
                    balance.setEmployeeId(employeeId);
                    balance.setYear(year);
                    balance.setAnnualLeave(20);
                    balance.setSickLeave(10);
                    balance.setPersonalLeave(5);
                    balance.setAnnualUsed(0);
                    balance.setSickUsed(0);
                    balance.setPersonalUsed(0);
                    return leaveBalanceRepository.save(balance);
                });
    }

    public boolean hasApprovedLeave(Long employeeId, LocalDate date) {
        List<LeaveRequest> leaves = leaveRequestRepository
                .findByEmployeeIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        employeeId, LeaveStatus.APPROVED, date, date);
        return !leaves.isEmpty();
    }

    // ---------- helpers ----------

    /** Applicants with no recorded role (legacy rows) fall back to ADMIN-only review. */
    static String requiredApproverRole(String applicantRole) {
        if (applicantRole == null) return "ADMIN";
        return switch (applicantRole) {
            case "EMPLOYEE" -> "HR";
            case "HR" -> "ADMIN";
            default -> "ADMIN";
        };
    }

    private static String normaliseRole(String role) {
        return role == null || role.isBlank() ? null : role.trim().toUpperCase();
    }

    private static Long applicantUserIdOf(LeaveRequest r) {
        return r.getApplicantUserId() != null ? r.getApplicantUserId() : r.getEmployeeId();
    }

    private static String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private int getRemainingForType(LeaveBalance balance, LeaveType type) {
        return switch (type) {
            case ANNUAL -> balance.getAnnualLeave() - balance.getAnnualUsed();
            case SICK -> balance.getSickLeave() - balance.getSickUsed();
            case PERSONAL -> balance.getPersonalLeave() - balance.getPersonalUsed();
            case UNPAID -> Integer.MAX_VALUE;
        };
    }

    private void deductBalance(LeaveBalance balance, LeaveType type, int days) {
        switch (type) {
            case ANNUAL -> balance.setAnnualUsed(balance.getAnnualUsed() + days);
            case SICK -> balance.setSickUsed(balance.getSickUsed() + days);
            case PERSONAL -> balance.setPersonalUsed(balance.getPersonalUsed() + days);
            case UNPAID -> { /* no deduction */ }
        }
    }

    private LeaveResponse toResponse(LeaveRequest r) {
        return new LeaveResponse(
                r.getId(), r.getEmployeeId(), r.getLeaveType(),
                r.getStartDate(), r.getEndDate(), r.getReason(),
                r.getStatus(), r.getReviewedBy(), r.getReviewNotes(), r.getReviewedAt(),
                r.getCreatedAt(), r.getUpdatedAt(),
                applicantUserIdOf(r), r.getApplicantRole(), r.getApplicantName()
        );
    }

    private LeaveBalanceResponse toBalanceResponse(LeaveBalance b) {
        return new LeaveBalanceResponse(
                b.getId(), b.getEmployeeId(),
                b.getAnnualLeave(), b.getSickLeave(), b.getPersonalLeave(),
                b.getAnnualUsed(), b.getSickUsed(), b.getPersonalUsed(),
                b.getAnnualLeave() - b.getAnnualUsed(),
                b.getSickLeave() - b.getSickUsed(),
                b.getPersonalLeave() - b.getPersonalUsed(),
                b.getYear()
        );
    }
}
