package com.shiftscheduling.attendance.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;

/** Subset of leave-service LeaveResponse. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RemoteLeave(Long employeeId, String leaveType, LocalDate startDate, LocalDate endDate, String status) {}
