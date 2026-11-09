package com.event_booking_app.ai_service.dto;

import java.util.UUID;

public record ChatResponse(UUID conversationId, String reply) {
}