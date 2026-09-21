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
public class CreateInventoryRequest {
    @NotNull(message = "Event ID is required")
    private UUID eventId;

    @NotNull(message = "ticket type ID is required")
    private UUID ticketTypeId;

    @NotNull(message = "totalQuantity is required")
    @Min(value = 0, message = "totalQuantity cannot be negative")
    private int totalQuantity;
}
