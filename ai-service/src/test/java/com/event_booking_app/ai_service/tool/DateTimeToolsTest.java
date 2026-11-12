package com.event_booking_app.ai_service.tool;

import com.event_booking_app.ai_service.config.AiProperties;
import com.event_booking_app.ai_service.tool.dto.CurrentDate;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class DateTimeToolsTest {

    @Test
    void returnsDateInConfiguredZoneNotUtc() {
        // 20:00 UTC on Sunday is already 01:30 on Monday in Asia/Kolkata (UTC+05:30).
        Clock clock = Clock.fixed(Instant.parse("2026-11-08T20:00:00Z"), ZoneOffset.UTC);
        DateTimeTools tools = new DateTimeTools(clock, properties(ZoneId.of("Asia/Kolkata")));

        CurrentDate today = tools.getCurrentDate();

        assertThat(today.date()).isEqualTo("2026-11-09");
        assertThat(today.dayOfWeek()).isEqualTo("MONDAY");
    }

    @Test
    void usesUtcDateWhenZoneIsUtc() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-08T20:00:00Z"), ZoneOffset.UTC);
        DateTimeTools tools = new DateTimeTools(clock, properties(ZoneOffset.UTC));

        assertThat(tools.getCurrentDate()).isEqualTo(new CurrentDate("2026-11-08", "SUNDAY"));
    }

    private static AiProperties properties(ZoneId zone) {
        return new AiProperties("prompt", 20, Duration.ofSeconds(60), zone);
    }
}
