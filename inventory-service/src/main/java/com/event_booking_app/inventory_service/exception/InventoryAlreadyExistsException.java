package com.event_booking_app.inventory_service.exception;

import java.util.UUID;

public class InventoryAlreadyExistsException extends RuntimeException {
    public InventoryAlreadyExistsException(String message) {
        super(message);
    }

    public InventoryAlreadyExistsException(UUID eventId, UUID ticketTypeId) {
        super("Inventory already exists for eventId: " + eventId + ", ticketTypeId: " + ticketTypeId);
    }

}
