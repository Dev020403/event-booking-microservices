package com.event_booking_app.booking_service.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryResponse {
    private UUID id;
    private UUID eventId;
    private UUID ticketTypeId;
    private int totalQuantity;
    private int availableQuantity;
    private int reservedQuantity;
    private int soldQuantity;
}
