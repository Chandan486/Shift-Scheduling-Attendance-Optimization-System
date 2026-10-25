package com.shiftscheduling.notification.dto;

import com.shiftscheduling.notification.entity.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record NotificationRequest(
    @NotNull Long userId,
    @NotBlank String title,
    @NotBlank String message,
    @NotNull NotificationType type,
    Long referenceId,
    String dedupKey
) {}
