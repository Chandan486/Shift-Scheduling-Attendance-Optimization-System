package com.shiftscheduling.leave.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Payload posted to notification-service /api/notifications/leave-decision. Recipient = applicant. */
public record LeaveDecisionNotification(
    Long userId,
    Long leaveId,
    Boolean approved,
    String reason,
    String leaveType,
    LocalDate startDate,
    LocalDate endDate,
    String approverName,
    LocalDateTime decidedAt,
    String dedupKey
) {}
