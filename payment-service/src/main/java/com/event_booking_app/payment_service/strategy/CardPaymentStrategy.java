package com.event_booking_app.payment_service.strategy;

import com.event_booking_app.payment_service.entity.Payment;
import com.event_booking_app.payment_service.entity.PaymentMethod;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

// Simulated gateway — no real Stripe/Razorpay integration. Deterministic
// test hook: an amount of exactly 13.00 always fails, so Booking Service's
// compensation flow (release the inventory hold) can be tested reliably
// without relying on randomness.
@Component
@Slf4j
public class CardPaymentStrategy implements PaymentStrategy {

    private static final BigDecimal FORCED_FAILURE_AMOUNT = new BigDecimal("13.00");

    @Override
    public PaymentResult charge(Payment payment) {
        log.info("Simulating card charge of {} {} for booking {}",
                payment.getAmount(), payment.getCurrency(), payment.getBookingId());

        if (payment.getAmount().compareTo(FORCED_FAILURE_AMOUNT) == 0) {
            return PaymentResult.failure("Card declined (simulated failure for testing)");
        }

        return PaymentResult.success("CARD-" + UUID.randomUUID());
    }

    @Override
    public PaymentResult refund(Payment payment) {
        log.info("Simulating card refund of {} {} for booking {}",
                payment.getAmount(), payment.getCurrency(), payment.getBookingId());
        return PaymentResult.success("REFUND-" + UUID.randomUUID());
    }

    @Override
    public PaymentMethod supports() {
        return PaymentMethod.CARD;
    }
}