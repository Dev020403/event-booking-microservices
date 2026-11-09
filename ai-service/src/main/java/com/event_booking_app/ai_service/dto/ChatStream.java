package com.event_booking_app.ai_service.dto;

import reactor.core.publisher.Flux;

import java.util.UUID;

public record ChatStream(UUID conversationId, Flux<String> tokens) {
}
