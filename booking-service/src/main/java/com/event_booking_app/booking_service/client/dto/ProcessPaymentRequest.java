package com.event_booking_app.booking_service.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessPaymentRequest {
    private UUID bookingId;
    private BigDecimal amount;
    private String currency;
    private PaymentMethod paymentMethod;
    private UUID idempotencyKey;
}
