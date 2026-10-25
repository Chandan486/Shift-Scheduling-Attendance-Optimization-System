package com.shiftscheduling.scheduling.dto;

public record ShiftRequirementResponse(
    Long id,
    Long skillId,
    int requiredCount
) {}
