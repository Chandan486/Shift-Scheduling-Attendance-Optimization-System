package com.shiftscheduling.scheduling.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record AssignmentResponse(
    Long id,
    Long scheduleId,
    Long shiftId,
    String shiftName,
    LocalTime shiftStartTime,
    LocalTime shiftEndTime,
    Long employeeId,
    LocalDate date,
    String status,
    String notes,
    LocalDateTime assignedAt
) {}
