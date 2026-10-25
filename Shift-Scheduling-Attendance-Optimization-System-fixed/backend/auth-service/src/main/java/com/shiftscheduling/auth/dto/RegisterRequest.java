package com.shiftscheduling.auth.dto;

import jakarta.validation.constraints.*;

public record RegisterRequest(
    @NotBlank(message = "Username is required")
    @Size(min = 4, max = 30, message = "Username must be 4-30 characters")
    @Pattern(regexp = "^[A-Za-z0-9_-]{4,30}$", message = "Username can only contain letters, digits, underscores and hyphens")
    String username,

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    String email,

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 100, message = "Password must be at least 6 characters")
    String password,

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100, message = "Full name must be 2-100 characters")
    String fullName,

    @NotBlank(message = "Role is required")
    String role
) {}
