package com.shiftscheduling.attendance.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Subset of employee-service EmployeeResponse needed by the overview report. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RemoteEmployee(Long id, String fullName, Long departmentId, String departmentName, boolean active) {}
