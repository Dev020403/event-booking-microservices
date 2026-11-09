package com.event_booking_app.ai_service.tool.dto;

import java.util.List;
import java.util.UUID;

public record EventAvailabilityResult(UUID eventId, List<TicketAvailability> ticketTypes, String message) {
    public static EventAvailabilityResult failure(UUID eventId, String message) {
        return new EventAvailabilityResult(eventId, List.of(), message);
    }
}