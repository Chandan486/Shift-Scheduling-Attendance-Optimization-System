package com.shiftscheduling.leave.dto;

import com.shiftscheduling.leave.entity.LeaveType;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record LeaveRequestDto(
    @NotNull(message = "Employee ID is required") Long employeeId,
    @NotNull(message = "Leave type is required") LeaveType leaveType,
    @NotNull(message = "Start date is required") LocalDate startDate,
    @NotNull(message = "End date is required") LocalDate endDate,
    @NotBlank(message = "Reason is required")
    @Size(min = 10, max = 500, message = "Reason must be 10-500 characters")
    String reason
) {}
