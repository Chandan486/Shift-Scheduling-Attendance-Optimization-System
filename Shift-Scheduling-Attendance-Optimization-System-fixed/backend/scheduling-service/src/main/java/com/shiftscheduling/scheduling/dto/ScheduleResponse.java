package com.shiftscheduling.scheduling.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ScheduleResponse(
    Long id,
    String name,
    LocalDate startDate,
    LocalDate endDate,
    String status,
    Long createdBy,
    String notes,
    int totalAssignments,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    LocalDateTime publishedAt
) {}
