package com.event_booking_app.event_service.kafka.event;

import java.util.UUID;

public record EventCancelledEvent(
        UUID eventId
) {
}
