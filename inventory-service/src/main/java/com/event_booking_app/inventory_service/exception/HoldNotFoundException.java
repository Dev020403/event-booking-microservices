package com.event_booking_app.inventory_service.exception;

import java.util.UUID;

public class HoldNotFoundException extends RuntimeException {
    public HoldNotFoundException(String message) {
        super(message);
    }

    public HoldNotFoundException(UUID holdId) {
        super("Hold not found with id: " + holdId);
    }

}
