package com.shiftscheduling.notification.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Sent by leave-service after a decision has been committed. The recipient is always the
 * applicant (userId), never the approver.
 */
public record LeaveDecisionRequest(
    @NotNull Long userId,
    @NotNull Long leaveId,
    @NotNull Boolean approved,
    String reason,
    String leaveType,
    LocalDate startDate,
    LocalDate endDate,
    String approverName,
    LocalDateTime decidedAt,
    String dedupKey
) {}
