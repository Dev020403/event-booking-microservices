package com.event_booking_app.inventory_service.exception;

import java.util.UUID;

public class InventoryNotFoundException extends RuntimeException {

    public InventoryNotFoundException(UUID inventoryId) {
        super("Inventory not found with id: " + inventoryId);
    }

    public InventoryNotFoundException(UUID eventId, UUID ticketTypeId) {
        super("Inventory not found for eventId: " + eventId + ", ticketTypeId: " + ticketTypeId);
    }
}