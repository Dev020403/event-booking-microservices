package com.event_booking_app.ai_service.dto;

import java.util.UUID;

/**
 * Payloads of the Server-Sent Events emitted by the streaming endpoint.
 */
public final class StreamEvents {

    private StreamEvents() {
    }

    public record Start(UUID conversationId) {
    }

    public record Token(String text) {
    }

    public record Done() {
    }

    public record Error(String message) {
    }
}