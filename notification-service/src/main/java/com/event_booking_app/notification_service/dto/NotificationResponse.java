package com.event_booking_app.notification_service.dto;

import com.event_booking_app.notification_service.entity.EmailStatus;
import com.event_booking_app.notification_service.entity.Notification;
import com.event_booking_app.notification_service.entity.NotificationType;

import java.time.Instant;
import java.util.UUID;

// Recipient address and delivery error details are deliberately not exposed.
public record NotificationResponse(
        UUID id,
        NotificationType type,
        String title,
        String message,
        UUID bookingId,
        boolean read,
        Instant readAt,
        EmailStatus emailStatus,
        Instant createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getType(), n.getTitle(), n.getMessage(), n.getBookingId(),
                n.isRead(), n.getReadAt(), n.getEmailStatus(), n.getCreatedAt());
    }
}
