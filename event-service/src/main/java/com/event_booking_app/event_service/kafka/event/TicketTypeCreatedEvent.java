package com.event_booking_app.event_service.kafka.event;

import java.util.UUID;

public record TicketTypeCreatedEvent(
        UUID eventId,
        UUID ticketTypeId,
        String name
) {
}
