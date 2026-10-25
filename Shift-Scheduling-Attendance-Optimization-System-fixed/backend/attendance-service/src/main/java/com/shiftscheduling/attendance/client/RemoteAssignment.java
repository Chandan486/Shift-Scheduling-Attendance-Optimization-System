package com.shiftscheduling.attendance.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.time.LocalTime;

/** Subset of scheduling-service AssignmentResponse. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RemoteAssignment(Long employeeId, LocalDate date, Long shiftId, String shiftName,
                               LocalTime shiftStartTime, LocalTime shiftEndTime, String status) {}
