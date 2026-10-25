package com.shiftscheduling.attendance.dto;

import jakarta.validation.constraints.NotNull;

public record CheckOutRequest(
    @NotNull(message = "Employee ID is required")
    Long employeeId,
    String notes
) {}
