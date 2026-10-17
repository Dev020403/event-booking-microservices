package com.event_booking_app.booking_service.exception;

import java.util.UUID;

public class BookingAlreadyExistsException extends RuntimeException {
    public BookingAlreadyExistsException(UUID idempotencyKey) {
        super("Booking already exists for idempotency key: " + idempotencyKey);
    }
}
