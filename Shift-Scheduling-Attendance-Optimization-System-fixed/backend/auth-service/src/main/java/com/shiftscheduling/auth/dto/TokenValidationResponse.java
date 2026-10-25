package com.shiftscheduling.auth.dto;

public record TokenValidationResponse(boolean valid, Long userId, String username, String role) {}
