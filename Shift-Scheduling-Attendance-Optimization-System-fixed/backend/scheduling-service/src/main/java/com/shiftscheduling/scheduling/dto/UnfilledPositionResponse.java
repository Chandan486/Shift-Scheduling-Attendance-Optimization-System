package com.shiftscheduling.scheduling.dto;

import java.time.LocalDate;

public record UnfilledPositionResponse(
    Long shiftId,
    String shiftName,
    LocalDate date,
    int requiredCount,
    int assignedCount,
    String reason
) {}
