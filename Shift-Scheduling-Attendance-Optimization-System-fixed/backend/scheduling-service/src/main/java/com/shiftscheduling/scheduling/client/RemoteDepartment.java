package com.shiftscheduling.scheduling.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RemoteDepartment(Long id, String name) {}
