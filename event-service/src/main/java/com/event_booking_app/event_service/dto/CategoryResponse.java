package com.event_booking_app.event_service.dto;

public record CategoryResponse(
        String name,
        String displayName
) {
    public static CategoryResponse of(String name, String displayName) {
        return new CategoryResponse(name, displayName);
    }
}
