package com.event_booking_app.ai_service.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "eventmate.ai")
public record AiProperties(@NotBlank String systemPrompt) {
}
