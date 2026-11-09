package com.event_booking_app.ai_service.tool;

import com.event_booking_app.ai_service.config.AiProperties;
import com.event_booking_app.ai_service.tool.dto.CurrentDate;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class DateTimeTools {

    private final Clock clock;
    private final AiProperties properties;

    @Tool(description = """
            Get today's date and weekday. Call this before resolving relative dates \
            such as 'today', 'tomorrow' or 'this weekend'.""")
    public CurrentDate getCurrentDate() {
        LocalDate today = LocalDate.now(clock.withZone(properties.zoneId()));
        return new CurrentDate(today.toString(), today.getDayOfWeek().toString());
    }
}