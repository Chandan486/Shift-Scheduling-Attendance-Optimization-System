package com.shiftscheduling.scheduling.dto;

import java.util.List;

public record ScheduleGenerationResult(
    Long scheduleId,
    int totalAssignments,
    List<AssignmentResponse> assignments,
    List<UnfilledPositionResponse> unfilledPositions
) {}
