package com.event_booking_app.ai_service.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record VenueResponse(UUID id, String name, String address, String city, Integer capacity) {
}