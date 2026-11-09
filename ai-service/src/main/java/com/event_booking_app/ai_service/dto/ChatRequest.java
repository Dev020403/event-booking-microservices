package com.event_booking_app.ai_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = false)
public record ChatRequest(
        UUID conversationId,
        @NotBlank(message = "message must not be blank")
        @Size(max = MAX_MESSAGE_LENGTH, message = "message must be at most " + MAX_MESSAGE_LENGTH + " characters")
        String message) {

    public static final int MAX_MESSAGE_LENGTH = 2000;
}