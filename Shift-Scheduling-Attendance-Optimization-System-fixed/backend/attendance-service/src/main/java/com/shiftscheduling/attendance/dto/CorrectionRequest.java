package com.shiftscheduling.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record CorrectionRequest(
    @NotNull(message = "Corrected by (user ID) is required")
    Long correctedBy,
    LocalDateTime newCheckIn,
    LocalDateTime newCheckOut,
    @NotBlank(message = "Reason is required")
    String reason
) {}
