package com.event_booking_app.inventory_service.dto;

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
public class HoldRequest {
    @NotNull(message = "userId is required")
    private UUID userId;

    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "quantity must be at least 1")
    private Integer quantity;

    // Client-generated (e.g. by Booking Service) so a retried request with
    // the same key returns the existing hold instead of creating a duplicate.
    @NotNull(message = "idempotencyKey is required")
    private UUID idempotencyKey;
}
