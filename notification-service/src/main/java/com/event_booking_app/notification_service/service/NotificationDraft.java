package com.event_booking_app.notification_service.service;

import com.event_booking_app.notification_service.entity.NotificationType;

import java.util.UUID;

/**
 * A notification derived from an incoming event, before it is persisted.
 *
 * @param recipientEmail may be null — the notification is then in-app only
 */
public record NotificationDraft(
        UUID userId,
        UUID bookingId,
        NotificationType type,
        String title,
        String message,
        String recipientEmail
) {
    /** De-duplication key: each booking produces at most one notification per type. */
    public String sourceKey() {
        return type.name() + ":" + bookingId;
    }
}
