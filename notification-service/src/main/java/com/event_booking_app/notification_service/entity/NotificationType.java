package com.event_booking_app.notification_service.entity;

/**
 * One value per booking-service event type this service reacts to. The names match
 * the producer's "eventType" field exactly.
 */
public enum NotificationType {
    BOOKING_CREATED,
    BOOKING_CONFIRMED,
    BOOKING_CANCELLED,
    BOOKING_PAYMENT_FAILED,
    BOOKING_EXPIRED
}
