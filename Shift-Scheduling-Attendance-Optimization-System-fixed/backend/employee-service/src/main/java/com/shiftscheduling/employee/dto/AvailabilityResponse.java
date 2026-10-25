package com.shiftscheduling.employee.dto;
import java.time.DayOfWeek;
import java.time.LocalTime;
public record AvailabilityResponse(Long id, Long employeeId, DayOfWeek dayOfWeek,
    LocalTime startTime, LocalTime endTime, boolean available, String preference) {}
