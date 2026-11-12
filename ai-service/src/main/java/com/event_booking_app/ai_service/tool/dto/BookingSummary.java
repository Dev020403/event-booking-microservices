package com.event_booking_app.ai_service.tool.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** eventTitle is null when event-service could not be reached; eventId is always present. */
public record BookingSummary(UUID bookingId, UUID eventId, String eventTitle, String status,
                             Integer tickets, BigDecimal totalAmount, String currency, Instant bookedAt) {
}
