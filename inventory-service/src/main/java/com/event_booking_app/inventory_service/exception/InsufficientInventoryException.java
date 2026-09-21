package com.event_booking_app.inventory_service.exception;

import java.util.UUID;

public class InsufficientInventoryException extends RuntimeException {
    public InsufficientInventoryException(String message) {
        super(message);
    }
    public InsufficientInventoryException(UUID inventoryId, int requested, int available) {
        super("Cannot hold " + requested + " units for inventory " + inventoryId
                + " — only " + available + " available");
    }
}
