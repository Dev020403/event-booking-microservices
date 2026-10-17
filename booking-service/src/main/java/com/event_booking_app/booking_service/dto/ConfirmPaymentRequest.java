package com.event_booking_app.booking_service.dto;
import com.event_booking_app.booking_service.client.dto.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmPaymentRequest {
    @NotNull(message = "paymentMethod is required")
    private PaymentMethod paymentMethod;

    @NotNull(message = "idempotencyKey is required")
    private UUID idempotencyKey;
}