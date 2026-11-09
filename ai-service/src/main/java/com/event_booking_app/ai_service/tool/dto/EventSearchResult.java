package com.event_booking_app.ai_service.tool.dto;

import java.util.List;

public record EventSearchResult(List<EventSummary> events, String message) {
    public static EventSearchResult failure(String message) {
        return new EventSearchResult(List.of(), message);
    }
}