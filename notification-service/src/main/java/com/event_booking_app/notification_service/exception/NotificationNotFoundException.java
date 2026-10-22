package com.event_booking_app.notification_service.exception;

import java.util.UUID;

public class NotificationNotFoundException extends RuntimeException {
    public NotificationNotFoundException(UUID id) {
        super("Notification not found with id: " + id);
    }
}
