package com.shiftscheduling.scheduling.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record ScheduleGenerationRequest(
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate,
    Long departmentId
) {}
