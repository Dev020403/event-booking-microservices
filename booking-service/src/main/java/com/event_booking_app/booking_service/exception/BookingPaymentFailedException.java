package com.event_booking_app.booking_service.exception;

public class BookingPaymentFailedException extends RuntimeException {
    public BookingPaymentFailedException(String message) {
        super(message);
    }
}
