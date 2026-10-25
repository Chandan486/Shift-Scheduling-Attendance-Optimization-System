package com.shiftscheduling.notification.dto;

import com.shiftscheduling.notification.entity.NotificationType;
import java.time.LocalDateTime;

public record NotificationResponse(
    Long id,
    Long userId,
    String title,
    String message,
    NotificationType notificationType,
    Long referenceId,
    boolean isRead,
    LocalDateTime createdAt
) {}
