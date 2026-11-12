package com.event_booking_app.ai_service.tool;

import com.event_booking_app.ai_service.client.BookingClient;
import com.event_booking_app.ai_service.client.EventClient;
import com.event_booking_app.ai_service.client.dto.BookingPageResponse;
import com.event_booking_app.ai_service.client.dto.BookingResponse;
import com.event_booking_app.ai_service.client.dto.EventResponse;
import com.event_booking_app.ai_service.security.AuthenticatedUser;
import com.event_booking_app.ai_service.support.FeignErrors;
import com.event_booking_app.ai_service.tool.dto.BookingSummary;
import com.event_booking_app.ai_service.tool.dto.MyBookingsResult;
import com.event_booking_app.ai_service.tool.mapper.BookingToolMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingToolsTest {

    private static final AuthenticatedUser USER = new AuthenticatedUser(UUID.randomUUID(), "token-123");

    @Mock
    private BookingClient bookingClient;
    @Mock
    private EventClient eventClient;

    private BookingTools bookingTools;
    private ToolContext context;

    @BeforeEach
    void setUp() {
        bookingTools = new BookingTools(bookingClient, eventClient, new BookingToolMapper());
        context = new ToolContext(ToolUserContext.asMap(USER));
    }

    @Test
    void returnsNewestBookingsWithEventTitlesUsingTheCallersToken() {
        UUID eventId = UUID.randomUUID();
        BookingResponse booking = booking(eventId);
        when(bookingClient.getMyBookings(anyString(), anyInt(), anyInt(), anyString()))
                .thenReturn(new BookingPageResponse(List.of(booking), 1));
        when(eventClient.getEvent(eventId)).thenReturn(event(eventId, "Backend Conf"));

        MyBookingsResult result = bookingTools.getMyBookings(null, context);

        verify(bookingClient).getMyBookings("Bearer token-123", 0, BookingTools.DEFAULT_LIMIT,
                BookingTools.NEWEST_FIRST);
        assertThat(result.hasMore()).isFalse();
        assertThat(result.note()).isNull();
        assertThat(result.bookings()).singleElement().satisfies(summary -> {
            assertThat(summary.bookingId()).isEqualTo(booking.id());
            assertThat(summary.eventId()).isEqualTo(eventId);
            assertThat(summary.eventTitle()).isEqualTo("Backend Conf");
            assertThat(summary.tickets()).isEqualTo(2);
            assertThat(summary.status()).isEqualTo("CONFIRMED");
        });
    }

    @Test
    void looksUpEachEventTitleOnlyOnce() {
        UUID eventId = UUID.randomUUID();
        when(bookingClient.getMyBookings(anyString(), anyInt(), anyInt(), anyString()))
                .thenReturn(new BookingPageResponse(List.of(booking(eventId), booking(eventId)), 2));
        when(eventClient.getEvent(eventId)).thenReturn(event(eventId, "Backend Conf"));

        MyBookingsResult result = bookingTools.getMyBookings(5, context);

        assertThat(result.bookings()).extracting(BookingSummary::eventTitle).containsOnly("Backend Conf");
        verify(eventClient, times(1)).getEvent(eventId);
    }

    @Test
    void keepsBookingWhenEventTitleLookupFails() {
        UUID eventId = UUID.randomUUID();
        when(bookingClient.getMyBookings(anyString(), anyInt(), anyInt(), anyString()))
                .thenReturn(new BookingPageResponse(List.of(booking(eventId)), 1));
        when(eventClient.getEvent(eventId)).thenThrow(FeignErrors.status(404));

        MyBookingsResult result = bookingTools.getMyBookings(5, context);

        assertThat(result.bookings()).singleElement().satisfies(summary -> {
            assertThat(summary.eventId()).isEqualTo(eventId);
            assertThat(summary.eventTitle()).isNull();
        });
    }

    @Test
    void flagsHasMoreWhenTotalExceedsReturnedPage() {
        when(bookingClient.getMyBookings(anyString(), anyInt(), anyInt(), anyString()))
                .thenReturn(new BookingPageResponse(List.of(booking(null)), 7));

        MyBookingsResult result = bookingTools.getMyBookings(1, context);

        assertThat(result.hasMore()).isTrue();
        verifyNoInteractions(eventClient);
    }

    @Test
    void reportsWhenUserHasNoBookings() {
        when(bookingClient.getMyBookings(anyString(), anyInt(), anyInt(), anyString()))
                .thenReturn(new BookingPageResponse(null, 0));

        MyBookingsResult result = bookingTools.getMyBookings(null, context);

        assertThat(result.bookings()).isEmpty();
        assertThat(result.note()).isEqualTo("The user has no bookings.");
    }

    @ParameterizedTest
    @CsvSource({"0, 1", "-3, 1", "3, 3", "10, 10", "50, 10"})
    void clampsRequestedLimit(int requested, int expected) {
        when(bookingClient.getMyBookings(anyString(), anyInt(), anyInt(), anyString()))
                .thenReturn(new BookingPageResponse(List.of(), 0));

        bookingTools.getMyBookings(requested, context);

        verify(bookingClient).getMyBookings(anyString(), eq(0), eq(expected), anyString());
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403})
    void asksUserToSignInAgainOnAuthFailure(int status) {
        when(bookingClient.getMyBookings(anyString(), anyInt(), anyInt(), anyString()))
                .thenThrow(FeignErrors.status(status));

        MyBookingsResult result = bookingTools.getMyBookings(null, context);

        assertThat(result.bookings()).isEmpty();
        assertThat(result.note()).contains("sign in again");
    }

    @Test
    void returnsTemporarilyUnavailableOnServerError() {
        when(bookingClient.getMyBookings(anyString(), anyInt(), anyInt(), anyString()))
                .thenThrow(FeignErrors.status(502));

        assertThat(bookingTools.getMyBookings(null, context).note()).contains("temporarily unavailable");
    }

    @Test
    void returnsTemporarilyUnavailableOnNonFeignFailure() {
        when(bookingClient.getMyBookings(anyString(), anyInt(), anyInt(), anyString()))
                .thenThrow(new IllegalStateException("decode failed"));

        assertThat(bookingTools.getMyBookings(null, context).note()).contains("temporarily unavailable");
    }

    @Test
    void failsClosedWithoutAuthenticatedUser() {
        ToolContext empty = new ToolContext(Map.of());

        assertThatThrownBy(() -> bookingTools.getMyBookings(null, empty))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(bookingClient, eventClient);
    }

    private static BookingResponse booking(UUID eventId) {
        return new BookingResponse(UUID.randomUUID(), eventId, "CONFIRMED", 2, new BigDecimal("10000.00"),
                "INR", Instant.parse("2026-11-01T10:00:00Z"));
    }

    private static EventResponse event(UUID id, String title) {
        return new EventResponse(id, title, null, null, null, null, null);
    }
}
