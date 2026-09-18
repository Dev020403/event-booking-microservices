package com.event_booking_app.event_service.exception;

import java.util.UUID;

public class TicketTypeNotFoundException extends RuntimeException {
    public TicketTypeNotFoundException(UUID id) {
        super("Ticket type not found with id: " + id);
    }

    public TicketTypeNotFoundException(String message) {
        super(message);
    }
}
