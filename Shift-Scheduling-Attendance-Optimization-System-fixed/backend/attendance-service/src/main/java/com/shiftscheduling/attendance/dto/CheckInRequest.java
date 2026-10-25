package com.shiftscheduling.attendance.dto;

import jakarta.validation.constraints.NotNull;

public record CheckInRequest(
    @NotNull(message = "Employee ID is required")
    Long employeeId,
    Long shiftId,
    String notes
) {}
