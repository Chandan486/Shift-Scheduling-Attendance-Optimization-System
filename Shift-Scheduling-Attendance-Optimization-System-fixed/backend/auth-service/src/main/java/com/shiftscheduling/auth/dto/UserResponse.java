package com.shiftscheduling.auth.dto;

import java.time.LocalDateTime;

public record UserResponse(Long id, String username, String email, String fullName, String role, boolean active, LocalDateTime createdAt) {}
