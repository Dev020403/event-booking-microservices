package com.event_booking_app.ai_service.dto;

import java.time.Instant;
import java.util.UUID;

public record ConversationSummary(UUID id, String title, Instant createdAt, Instant updatedAt) {
}