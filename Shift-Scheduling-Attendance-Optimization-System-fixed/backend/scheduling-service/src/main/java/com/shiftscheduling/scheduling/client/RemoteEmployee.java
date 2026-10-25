package com.shiftscheduling.scheduling.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Subset of employee-service's EmployeeResponse that scheduling needs. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RemoteEmployee(
    Long id,
    Long userId,
    String fullName,
    Long departmentId,
    String departmentName,
    boolean active
) {}
