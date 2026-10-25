package com.shiftscheduling.scheduling.dto;

/** Body for notification-service POST /api/notifications (same shape as its NotificationRequest). */
public record NotificationPayload(
    Long userId,
    String title,
    String message,
    String type,
    Long referenceId,
    String dedupKey
) {}
