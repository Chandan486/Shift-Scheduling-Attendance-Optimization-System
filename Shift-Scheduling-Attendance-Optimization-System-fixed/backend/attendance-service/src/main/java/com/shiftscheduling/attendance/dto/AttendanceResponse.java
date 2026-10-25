package com.shiftscheduling.attendance.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AttendanceResponse(
    Long id,
    Long employeeId,
    Long shiftId,
    LocalDate date,
    LocalDateTime checkInTime,
    LocalDateTime checkOutTime,
    String status,
    Double workedHours,
    String notes,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
