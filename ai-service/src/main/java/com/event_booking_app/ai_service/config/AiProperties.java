package com.event_booking_app.ai_service.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.ZoneId;

@Validated
@ConfigurationProperties(prefix = "eventmate.ai")
public record AiProperties(
        @NotBlank String systemPrompt,
        @DefaultValue("20") @Min(2) @Max(100) int memoryWindowSize,
        @DefaultValue("60s") @NotNull Duration streamIdleTimeout,
        @DefaultValue("Asia/Kolkata") @NotNull ZoneId zoneId) {
}
