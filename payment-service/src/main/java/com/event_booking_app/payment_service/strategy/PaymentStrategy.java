package com.event_booking_app.payment_service.strategy;

import com.event_booking_app.payment_service.entity.Payment;
import com.event_booking_app.payment_service.entity.PaymentMethod;

public interface PaymentStrategy {
    PaymentResult charge(Payment payment);

    PaymentResult refund(Payment payment);

    PaymentMethod supports();
}
