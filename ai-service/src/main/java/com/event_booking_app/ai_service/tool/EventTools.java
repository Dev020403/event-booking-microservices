package com.event_booking_app.ai_service.tool;

import com.event_booking_app.ai_service.client.EventClient;
import com.event_booking_app.ai_service.client.InventoryClient;
import com.event_booking_app.ai_service.client.dto.EventCategory;
import com.event_booking_app.ai_service.client.dto.EventPage;
import com.event_booking_app.ai_service.client.dto.EventResponse;
import com.event_booking_app.ai_service.tool.dto.EventAvailabilityResult;
import com.event_booking_app.ai_service.tool.dto.EventSearchResult;
import com.event_booking_app.ai_service.tool.dto.EventSummary;
import com.event_booking_app.ai_service.tool.dto.TicketAvailability;
import com.event_booking_app.ai_service.tool.mapper.EventToolMapper;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventTools {

    private static final int MAX_RESULTS = 5;
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final EventClient eventClient;
    private final InventoryClient inventoryClient;

    @Tool(description = """
            Search upcoming published events. Every filter is optional. \
            Use when the user asks what is on, or wants events by category, city or date.""")
    public EventSearchResult searchEvents(
            @ToolParam(description = "Event category", required = false) EventCategory category,
            @ToolParam(description = "City name, e.g. Ahmedabad", required = false) String city,
            @ToolParam(description = "Earliest start date, format YYYY-MM-DD", required = false) String fromDate,
            @ToolParam(description = "Latest start date, format YYYY-MM-DD", required = false) String toDate) {

        final LocalDate from;
        final LocalDate to;
        try {
            from = parseDate(fromDate);
            to = parseDate(toDate);
        } catch (DateTimeParseException ex) {
            return EventSearchResult.failure("Dates must use the format YYYY-MM-DD.");
        }
        if (from != null && to != null && from.isAfter(to)) {
            return EventSearchResult.failure("fromDate must be on or before toDate.");
        }

        try {
            EventPage page = eventClient.searchEvents(
                    category,
                    blankToNull(city),
                    from == null ? null : from.atStartOfDay().format(ISO),
                    to == null ? null : to.atTime(23, 59, 59).format(ISO),
                    MAX_RESULTS);
            List<EventSummary> events = page.content().stream().map(EventToolMapper::toSummary).toList();
            return new EventSearchResult(events, events.isEmpty() ? "No matching events found." : null);
        } catch (RuntimeException ex) {
            // Tool boundary: log details, give the model a safe message.
            log.warn("searchEvents failed", ex);
            return EventSearchResult.failure("Event search is temporarily unavailable.");
        }
    }

    @Tool(description = """
            Get the ticket types of one event with price, sales window and remaining quantity. \
            availableQuantity is null when it could not be determined. \
            Use the eventId returned by searchEvents.""")
    public EventAvailabilityResult getEventTicketAvailability(
            @ToolParam(description = "Event id (UUID) returned by searchEvents") UUID eventId) {
        try {
            List<TicketAvailability> items = eventClient.getTicketTypes(eventId).stream()
                    .map(type -> EventToolMapper.toAvailability(type, availableQuantity(eventId, type.id())))
                    .toList();
            return new EventAvailabilityResult(eventId, items,
                    items.isEmpty() ? "No ticket types are available for this event." : null);
        } catch (FeignException.NotFound ex) {
            return EventAvailabilityResult.failure(eventId, "Event not found.");
        } catch (FeignException ex) {
            log.warn("getEventTicketAvailability Feign error (eventId={}, status={}, body={})",
                    eventId, ex.status(), ex.contentUTF8(), ex);
            return EventAvailabilityResult.failure(eventId, "Ticket availability is temporarily unavailable.");
        } catch (RuntimeException ex) {
            log.warn("getEventTicketAvailability failed (eventId={})", eventId, ex);
            return EventAvailabilityResult.failure(eventId, "Ticket availability is temporarily unavailable.");
        }
    }

    private Integer availableQuantity(UUID eventId, UUID ticketTypeId) {
        try {
            return inventoryClient.getInventory(eventId, ticketTypeId).availableQuantity();
        } catch (FeignException ex) {
            log.warn("Inventory Feign error (eventId={}, ticketTypeId={}, status={}, body={})",
                    eventId, ticketTypeId, ex.status(), ex.contentUTF8());
            return null;
        } catch (RuntimeException ex) {
            log.warn("Inventory lookup failed (eventId={}, ticketTypeId={})", eventId, ticketTypeId, ex);
            return null;
        }
    }

    private static LocalDate parseDate(String value) {
        return (value == null || value.isBlank()) ? null : LocalDate.parse(value.trim());
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}