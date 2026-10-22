package com.event_booking_app.notification_service.entity;

public enum EmailStatus {
    /** Waiting to be sent (first attempt not made yet). */
    PENDING,
    /** Claimed by a sender; guards against the listener and retry job sending twice. */
    SENDING,
    SENT,
    /** Last attempt failed; retried until max attempts is reached. */
    FAILED,
    /** Not sent: email disabled or no recipient address on the event. */
    SKIPPED
}
