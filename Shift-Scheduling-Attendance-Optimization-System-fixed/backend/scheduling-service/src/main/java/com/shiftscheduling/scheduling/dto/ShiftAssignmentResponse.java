package com.shiftscheduling.scheduling.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record ShiftAssignmentResponse(
    Long id,
    Long shiftId,
    String shiftName,
    LocalDate date,
    LocalTime startTime,
    LocalTime endTime,
    Long departmentId,
    Long employeeId,
    String employeeName,
    Long userId,
    String status,
    LocalDateTime assignedAt
) {}
