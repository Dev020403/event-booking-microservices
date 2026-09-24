package com.event_booking_app.payment_service.exception;

import com.event_booking_app.payment_service.entity.PaymentMethod;

public class UnsupportedPaymentMethodException extends RuntimeException {
    public UnsupportedPaymentMethodException(PaymentMethod method) {
        super("No strategy registered for payment method: " + method);
    }
}