package com.event_booking_app.ai_service.tool.mapper;

import com.event_booking_app.ai_service.client.dto.EventCategory;
import com.event_booking_app.ai_service.client.dto.EventResponse;
import com.event_booking_app.ai_service.client.dto.TicketTypeResponse;
import com.event_booking_app.ai_service.client.dto.VenueResponse;
import com.event_booking_app.ai_service.tool.dto.EventSummary;
import com.event_booking_app.ai_service.tool.dto.TicketAvailability;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventToolMapperTest {

    @Test
    void mapsEventWithVenue() {
        UUID id = UUID.randomUUID();
        EventResponse event = new EventResponse(id, "Conf", "Short", EventCategory.CONFERENCE,
                LocalDateTime.of(2027, 2, 16, 18, 0), null,
                new VenueResponse(UUID.randomUUID(), "Stadium", "Road", "Ahmedabad", 100));

        EventSummary summary = EventToolMapper.toSummary(event);

        assertThat(summary).isEqualTo(new EventSummary(id, "Conf", "CONFERENCE", "Ahmedabad", "Stadium",
                "2027-02-16T18:00", "Short"));
    }

    @Test
    void toleratesMissingVenueCategoryAndStart() {
        EventSummary summary = EventToolMapper.toSummary(
                new EventResponse(UUID.randomUUID(), "Conf", null, null, null, null, null));

        assertThat(summary.city()).isNull();
        assertThat(summary.venue()).isNull();
        assertThat(summary.category()).isNull();
        assertThat(summary.startsAt()).isNull();
        assertThat(summary.description()).isNull();
    }

    @Test
    void truncatesLongDescriptionTo200CharsPlusEllipsis() {
        String longText = "x".repeat(250);

        EventSummary summary = EventToolMapper.toSummary(
                new EventResponse(UUID.randomUUID(), "Conf", longText, null, null, null, null));

        assertThat(summary.description()).hasSize(203).endsWith("...");
    }

    @Test
    void keepsDescriptionOfExactly200Chars() {
        String text = "y".repeat(200);

        EventSummary summary = EventToolMapper.toSummary(
                new EventResponse(UUID.randomUUID(), "Conf", text, null, null, null, null));

        assertThat(summary.description()).isEqualTo(text);
    }

    @Test
    void mapsTicketTypeAndPassesQuantityThrough() {
        UUID id = UUID.randomUUID();
        TicketTypeResponse type = new TicketTypeResponse(id, UUID.randomUUID(), "VIP", new BigDecimal("5000.00"),
                "INR", LocalDateTime.of(2026, 10, 1, 0, 0), null);

        TicketAvailability availability = EventToolMapper.toAvailability(type, null);

        assertThat(availability).isEqualTo(new TicketAvailability(id, "VIP", new BigDecimal("5000.00"), "INR",
                "2026-10-01T00:00", null, null));
    }
}
