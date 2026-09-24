package com.event_booking_app.payment_service.mapper;

import com.event_booking_app.payment_service.dto.PaymentResponse;
import com.event_booking_app.payment_service.entity.Payment;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {

    public PaymentResponse toResponse(Payment payment) {
        if (payment == null) {
            return null;
        }
        return PaymentResponse.builder()
                .id(payment.getId())
                .bookingId(payment.getBookingId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .paymentMethod(payment.getPaymentMethod())
                .status(payment.getStatus())
                .providerTransactionId(payment.getProviderTransactionId())
                .idempotencyKey(payment.getIdempotencyKey())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }
}
