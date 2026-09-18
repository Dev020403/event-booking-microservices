package com.event_booking_app.event_service.exception;

import java.util.UUID;

public class VenueNotFoundException extends RuntimeException {
    public VenueNotFoundException(UUID id) {
        super("Venue not found with id: " + id);
    }

    public VenueNotFoundException(String message) {
        super(message);
    }
}
