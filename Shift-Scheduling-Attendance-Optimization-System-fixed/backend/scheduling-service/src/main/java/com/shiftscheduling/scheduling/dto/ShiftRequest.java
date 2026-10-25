package com.shiftscheduling.scheduling.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * employeeIds: optional. When present on update, the shift's assignments are synchronised to exactly this set.
 * The server resolves each id against employee-service; client-side names or user ids are never trusted.
 */
public record ShiftRequest(
    @NotBlank @Size(max = 100) String name,
    @NotNull Long departmentId,
    @NotNull LocalTime startTime,
    @NotNull LocalTime endTime,
    @NotNull LocalDate date,
    boolean overnight,
    @Min(1) int maxStaff,
    @Min(1) int minStaff,
    boolean nightShift,
    List<ShiftRequirementRequest> requirements,
    List<Long> employeeIds
) {}
