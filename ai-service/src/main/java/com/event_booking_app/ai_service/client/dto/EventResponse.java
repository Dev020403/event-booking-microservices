package com.event_booking_app.ai_service.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EventResponse(UUID id, String title, String description, EventCategory category,
                            LocalDateTime startDateTime, LocalDateTime endDateTime, VenueResponse venue) {
}