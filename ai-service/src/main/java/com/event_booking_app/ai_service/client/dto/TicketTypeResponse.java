package com.event_booking_app.ai_service.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TicketTypeResponse(UUID id, UUID eventId, String name, BigDecimal price, String currency,
                                 LocalDateTime salesStartAt, LocalDateTime salesEndAt) {
}
