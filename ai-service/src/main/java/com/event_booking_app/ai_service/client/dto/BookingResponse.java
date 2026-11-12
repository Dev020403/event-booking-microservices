package com.event_booking_app.ai_service.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Subset of booking-service's BookingResponse. It carries no event title; that comes from event-service. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BookingResponse(UUID id, UUID eventId, String status, Integer quantity,
                              BigDecimal totalAmount, String currency, Instant createdAt) {
}
