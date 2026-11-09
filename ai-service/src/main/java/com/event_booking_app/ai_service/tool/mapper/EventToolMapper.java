package com.event_booking_app.ai_service.tool.mapper;

import com.event_booking_app.ai_service.client.dto.EventResponse;
import com.event_booking_app.ai_service.client.dto.TicketTypeResponse;
import com.event_booking_app.ai_service.client.dto.VenueResponse;
import com.event_booking_app.ai_service.tool.dto.EventSummary;
import com.event_booking_app.ai_service.tool.dto.TicketAvailability;

public final class EventToolMapper {

    private static final int MAX_DESCRIPTION_LENGTH = 200;

    private EventToolMapper() {
    }

    public static EventSummary toSummary(EventResponse event) {
        VenueResponse venue = event.venue();
        return new EventSummary(
                event.id(),
                event.title(),
                event.category() == null ? null : event.category().name(),
                venue == null ? null : venue.city(),
                venue == null ? null : venue.name(),
                event.startDateTime() == null ? null : event.startDateTime().toString(),
                truncate(event.description()));
    }

    public static TicketAvailability toAvailability(TicketTypeResponse type, Integer availableQuantity) {
        return new TicketAvailability(
                type.id(), type.name(), type.price(), type.currency(),
                type.salesStartAt() == null ? null : type.salesStartAt().toString(),
                type.salesEndAt() == null ? null : type.salesEndAt().toString(),
                availableQuantity);
    }

    private static String truncate(String text) {
        if (text == null || text.length() <= MAX_DESCRIPTION_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_DESCRIPTION_LENGTH) + "...";
    }
}