package com.event_booking_app.payment_service.strategy;

import com.event_booking_app.payment_service.entity.Payment;
import com.event_booking_app.payment_service.entity.PaymentMethod;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
public class UpiPaymentStrategy implements PaymentStrategy{
    @Override
    public PaymentResult charge(Payment payment) {
        log.info("Simulating UPI charge of {} {} for booking {}",
                payment.getAmount(), payment.getCurrency(), payment.getBookingId());
        return PaymentResult.success("UPI-" + UUID.randomUUID());
    }

    @Override
    public PaymentResult refund(Payment payment) {
        log.info("Simulating UPI refund of {} {} for booking {}",
                payment.getAmount(), payment.getCurrency(), payment.getBookingId());
        return PaymentResult.success("REFUND-" + UUID.randomUUID());
    }

    @Override
    public PaymentMethod supports() {
        return PaymentMethod.UPI;
    }
}
