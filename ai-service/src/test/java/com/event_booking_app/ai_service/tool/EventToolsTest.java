package com.event_booking_app.ai_service.tool;

import com.event_booking_app.ai_service.client.EventClient;
import com.event_booking_app.ai_service.client.InventoryClient;
import com.event_booking_app.ai_service.client.dto.EventCategory;
import com.event_booking_app.ai_service.client.dto.EventPage;
import com.event_booking_app.ai_service.client.dto.EventResponse;
import com.event_booking_app.ai_service.client.dto.InventoryResponse;
import com.event_booking_app.ai_service.client.dto.TicketTypeResponse;
import com.event_booking_app.ai_service.client.dto.VenueResponse;
import com.event_booking_app.ai_service.support.FeignErrors;
import com.event_booking_app.ai_service.tool.dto.EventAvailabilityResult;
import com.event_booking_app.ai_service.tool.dto.EventSearchResult;
import com.event_booking_app.ai_service.tool.dto.TicketAvailability;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventToolsTest {

    private static final UUID EVENT_ID = UUID.fromString("6827034e-2e2f-42da-a3df-b13b636831a7");

    @Mock
    private EventClient eventClient;
    @Mock
    private InventoryClient inventoryClient;
    @InjectMocks
    private EventTools eventTools;

    @Nested
    class SearchEvents {

        @Test
        void passesTrimmedFiltersAndFullDayRangeToEventService() {
            when(eventClient.searchEvents(any(), any(), any(), any(), anyInt()))
                    .thenReturn(new EventPage(List.of(event("Ahmedabad Backend Engineering Conference"))));

            EventSearchResult result = eventTools.searchEvents(
                    EventCategory.CONFERENCE, "  Ahmedabad ", "2027-02-01", "2027-02-28");

            verify(eventClient).searchEvents(EventCategory.CONFERENCE, "Ahmedabad",
                    "2027-02-01T00:00:00", "2027-02-28T23:59:59", 5);
            assertThat(result.message()).isNull();
            assertThat(result.events()).singleElement().satisfies(summary -> {
                assertThat(summary.eventId()).isEqualTo(EVENT_ID);
                assertThat(summary.city()).isEqualTo("Ahmedabad");
                assertThat(summary.category()).isEqualTo("CONFERENCE");
            });
        }

        @Test
        void sendsNullForBlankOptionalFilters() {
            when(eventClient.searchEvents(any(), any(), any(), any(), anyInt())).thenReturn(new EventPage(List.of()));

            EventSearchResult result = eventTools.searchEvents(null, "   ", "", null);

            verify(eventClient).searchEvents(null, null, null, null, 5);
            assertThat(result.events()).isEmpty();
            assertThat(result.message()).isEqualTo("No matching events found.");
        }

        @Test
        void rejectsMalformedDateWithoutCallingEventService() {
            EventSearchResult result = eventTools.searchEvents(null, null, "16-02-2027", null);

            assertThat(result.events()).isEmpty();
            assertThat(result.message()).contains("YYYY-MM-DD");
            verifyNoInteractions(eventClient);
        }

        @Test
        void rejectsInvertedDateRangeWithoutCallingEventService() {
            EventSearchResult result = eventTools.searchEvents(null, null, "2027-03-01", "2027-02-01");

            assertThat(result.message()).isEqualTo("fromDate must be on or before toDate.");
            verifyNoInteractions(eventClient);
        }

        @Test
        void returnsSafeMessageWhenEventServiceFails() {
            when(eventClient.searchEvents(any(), any(), any(), any(), anyInt())).thenThrow(FeignErrors.status(500));

            EventSearchResult result = eventTools.searchEvents(null, "Ahmedabad", null, null);

            assertThat(result.events()).isEmpty();
            assertThat(result.message()).isEqualTo("Event search is temporarily unavailable.");
        }
    }

    @Nested
    class GetEventTicketAvailability {

        private final UUID vipId = UUID.randomUUID();
        private final UUID generalId = UUID.randomUUID();

        @Test
        void combinesTicketTypesWithInventory() {
            when(eventClient.getTicketTypes(EVENT_ID)).thenReturn(List.of(ticketType(vipId, "VIP")));
            when(inventoryClient.getInventory(EVENT_ID, vipId))
                    .thenReturn(new InventoryResponse(UUID.randomUUID(), EVENT_ID, vipId, 5, 1));

            EventAvailabilityResult result = eventTools.getEventTicketAvailability(EVENT_ID);

            assertThat(result.eventId()).isEqualTo(EVENT_ID);
            assertThat(result.message()).isNull();
            assertThat(result.ticketTypes()).singleElement().satisfies(t -> {
                assertThat(t.name()).isEqualTo("VIP");
                assertThat(t.price()).isEqualByComparingTo("5000.00");
                assertThat(t.currency()).isEqualTo("INR");
                assertThat(t.availableQuantity()).isEqualTo(1);
            });
        }

        @Test
        void keepsTicketTypeWithNullQuantityWhenInventoryLookupFails() {
            when(eventClient.getTicketTypes(EVENT_ID))
                    .thenReturn(List.of(ticketType(vipId, "VIP"), ticketType(generalId, "General")));
            when(inventoryClient.getInventory(EVENT_ID, vipId)).thenThrow(FeignErrors.status(503));
            when(inventoryClient.getInventory(EVENT_ID, generalId))
                    .thenReturn(new InventoryResponse(UUID.randomUUID(), EVENT_ID, generalId, 100, 42));

            EventAvailabilityResult result = eventTools.getEventTicketAvailability(EVENT_ID);

            assertThat(result.ticketTypes()).extracting(TicketAvailability::availableQuantity)
                    .containsExactly(null, 42);
        }

        @Test
        void reportsNoTicketTypes() {
            when(eventClient.getTicketTypes(EVENT_ID)).thenReturn(List.of());

            EventAvailabilityResult result = eventTools.getEventTicketAvailability(EVENT_ID);

            assertThat(result.ticketTypes()).isEmpty();
            assertThat(result.message()).isEqualTo("No ticket types are available for this event.");
            verifyNoInteractions(inventoryClient);
        }

        @Test
        void mapsNotFoundToEventNotFound() {
            when(eventClient.getTicketTypes(EVENT_ID)).thenThrow(FeignErrors.status(404));

            EventAvailabilityResult result = eventTools.getEventTicketAvailability(EVENT_ID);

            assertThat(result.message()).isEqualTo("Event not found.");
            verify(inventoryClient, never()).getInventory(any(), any());
        }

        @Test
        void mapsServerErrorToTemporarilyUnavailable() {
            // The regression seen in practice: event-service answered 500 because of a schema problem.
            when(eventClient.getTicketTypes(EVENT_ID)).thenThrow(FeignErrors.status(500));

            EventAvailabilityResult result = eventTools.getEventTicketAvailability(EVENT_ID);

            assertThat(result.ticketTypes()).isEmpty();
            assertThat(result.message()).isEqualTo("Ticket availability is temporarily unavailable.");
        }

        @Test
        void mapsNonFeignFailureToTemporarilyUnavailable() {
            when(eventClient.getTicketTypes(EVENT_ID)).thenThrow(new IllegalStateException("decode failed"));

            EventAvailabilityResult result = eventTools.getEventTicketAvailability(EVENT_ID);

            assertThat(result.message()).isEqualTo("Ticket availability is temporarily unavailable.");
        }
    }

    private static EventResponse event(String title) {
        VenueResponse venue = new VenueResponse(UUID.randomUUID(), "Narendra Modi Stadium", "Motera Road",
                "Ahmedabad", 50000);
        return new EventResponse(EVENT_ID, title, "Java, Spring Boot, Kafka", EventCategory.CONFERENCE,
                LocalDateTime.of(2027, 2, 16, 18, 0), LocalDateTime.of(2027, 2, 16, 21, 0), venue);
    }

    private static TicketTypeResponse ticketType(UUID id, String name) {
        return new TicketTypeResponse(id, EVENT_ID, name, new BigDecimal("5000.00"), "INR",
                LocalDateTime.of(2026, 10, 1, 0, 0), LocalDateTime.of(2026, 12, 14, 23, 59, 59));
    }
}
