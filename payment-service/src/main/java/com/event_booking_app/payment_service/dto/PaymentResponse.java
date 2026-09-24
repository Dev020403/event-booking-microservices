package com.event_booking_app.payment_service.dto;

import com.event_booking_app.payment_service.entity.PaymentMethod;
import com.event_booking_app.payment_service.entity.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    private UUID id;
    private UUID bookingId;
    private BigDecimal amount;
    private String currency;
    private PaymentMethod paymentMethod;
    private PaymentStatus status;
    private String providerTransactionId;
    private UUID idempotencyKey;
    private Instant createdAt;
    private Instant updatedAt;
}
