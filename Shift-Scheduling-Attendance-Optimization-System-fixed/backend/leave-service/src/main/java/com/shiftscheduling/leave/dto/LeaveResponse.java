package com.shiftscheduling.leave.dto;

import com.shiftscheduling.leave.entity.LeaveStatus;
import com.shiftscheduling.leave.entity.LeaveType;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record LeaveResponse(
    Long id,
    Long employeeId,
    LeaveType leaveType,
    LocalDate startDate,
    LocalDate endDate,
    String reason,
    LeaveStatus status,
    Long reviewedBy,
    String reviewNotes,
    LocalDateTime reviewedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    Long applicantUserId,
    String applicantRole,
    String applicantName
) {}
