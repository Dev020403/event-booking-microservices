package com.event_booking_app.payment_service.exception;

import java.util.UUID;

public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(UUID id) {
        super("Payment not found with id: " + id);
    }

    public PaymentNotFoundException(String message) {
        super(message);
    }
}
