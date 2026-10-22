package com.event_booking_app.booking_service.client.dto;

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
    // String, not PaymentMethod: Payment Service reports card payments as "CARD",
    // which isn't in Booking's enum and would fail deserialization of a successful charge.
    private String paymentMethod;
    private PaymentStatus status;
    private String providerTransactionId;
    private UUID idempotencyKey;
    private Instant createdAt;
    private Instant updatedAt;
}
