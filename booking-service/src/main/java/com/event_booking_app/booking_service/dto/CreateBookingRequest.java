package com.event_booking_app.booking_service.dto;
import com.event_booking_app.booking_service.client.dto.PaymentMethod;
import jakarta.validation.constraints.Min;
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
public class CreateBookingRequest {
    @NotNull(message = "eventId is required")
    private UUID eventId;

    @NotNull(message = "ticketTypeId is required")
    private UUID ticketTypeId;

    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "quantity must be at least 1")
    private Integer quantity;

    private PaymentMethod paymentMethod;

    @NotNull(message = "idempotencyKey is required")
    private UUID idempotencyKey;
}