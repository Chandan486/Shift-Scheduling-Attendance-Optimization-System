package com.shiftscheduling.scheduling.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public record ShiftResponse(
    Long id,
    String name,
    Long departmentId,
    LocalTime startTime,
    LocalTime endTime,
    LocalDate date,
    boolean overnight,
    int maxStaff,
    int minStaff,
    boolean nightShift,
    List<ShiftRequirementResponse> requirements,
    LocalDateTime createdAt
) {}
