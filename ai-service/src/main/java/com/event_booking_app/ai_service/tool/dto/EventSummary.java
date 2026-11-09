package com.event_booking_app.ai_service.tool.dto;

import java.util.UUID;

public record EventSummary(UUID eventId, String title, String category, String city,
                           String venue, String startsAt, String description) {
}
