package com.shiftscheduling.scheduling.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ShiftRequirementRequest(
    @NotNull Long skillId,
    @Min(1) int requiredCount
) {}
