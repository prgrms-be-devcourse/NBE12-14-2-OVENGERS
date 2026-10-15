package com.ovengers.slotkey.notification.dto.response;

import com.ovengers.slotkey.notification.entity.Notification;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        Long waitlistEntryId,
        String message,
        boolean read,
        LocalDateTime createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getWaitlistEntryId(),
                notification.getMessage(),
                notification.getReadAt() != null,
                notification.getCreatedAt());
    }
}
