package com.event_booking_app.ai_service.tool;

import com.event_booking_app.ai_service.client.BookingClient;
import com.event_booking_app.ai_service.client.EventClient;
import com.event_booking_app.ai_service.client.dto.BookingPageResponse;
import com.event_booking_app.ai_service.client.dto.BookingResponse;
import com.event_booking_app.ai_service.security.AuthenticatedUser;
import com.event_booking_app.ai_service.tool.dto.BookingSummary;
import com.event_booking_app.ai_service.tool.dto.MyBookingsResult;
import com.event_booking_app.ai_service.tool.mapper.BookingToolMapper;
import feign.FeignException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingTools {

    static final int DEFAULT_LIMIT = 5;
    static final int MAX_LIMIT = 10;
    /** booking-service sorts ascending by default; the tool promises most recent first. */
    static final String NEWEST_FIRST = "createdAt,desc";

    private final BookingClient bookingClient;
    private final EventClient eventClient;
    private final BookingToolMapper mapper;

    @Tool(description = """
            Lists the signed-in user's own bookings, most recent first. Use this when the user asks \
            about their bookings, tickets or reservations. The user is identified by the system; \
            never ask for or accept a user id. This tool cannot show anyone else's bookings.""")
    public MyBookingsResult getMyBookings(
            @ToolParam(description = "Maximum number of bookings to return, 1 to 10. Defaults to 5.",
                    required = false) Integer limit,
            ToolContext toolContext) {

        // Outside the try on purpose: a missing user is a programming error and must fail closed.
        AuthenticatedUser user = ToolUserContext.require(toolContext);
        int size = clamp(limit);
        try {
            BookingPageResponse page = bookingClient.getMyBookings(user.bearerToken(), 0, size, NEWEST_FIRST);
            List<BookingResponse> content = page.content() == null ? List.of() : page.content();

            Map<UUID, Optional<String>> titles = new HashMap<>();
            List<BookingSummary> bookings = content.stream()
                    .map(b -> mapper.toSummary(b, titles.computeIfAbsent(b.eventId(), this::eventTitle).orElse(null)))
                    .toList();
            return MyBookingsResult.of(bookings, page.totalElements() > bookings.size());
        } catch (FeignException ex) {
            // status -1 means no HTTP response at all (connect/read timeout, refused); the message says which.
            // The message holds method + URL only; the Authorization header is not included.
            log.warn("getMyBookings failed (userId={}, status={}): {}", user.id(), ex.status(), ex.getMessage());
            if (ex.status() == 401 || ex.status() == 403) {
                return MyBookingsResult.unavailable(
                        "The user's session is not allowed to view bookings. Ask them to sign in again.");
            }
            return MyBookingsResult.unavailable("Bookings are temporarily unavailable. Ask the user to try again later.");
        } catch (RuntimeException ex) {
            // Tool boundary: decoding or connection errors must not abort the whole chat turn.
            log.warn("getMyBookings failed (userId={})", user.id(), ex);
            return MyBookingsResult.unavailable("Bookings are temporarily unavailable. Ask the user to try again later.");
        }
    }

    /** Best effort: a booking is still useful without its event title. */
    private Optional<String> eventTitle(UUID eventId) {
        if (eventId == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(eventClient.getEvent(eventId).title());
        } catch (RuntimeException ex) {
            log.warn("Event title lookup failed (eventId={}): {}", eventId, ex.getMessage());
            return Optional.empty();
        }
    }

    private static int clamp(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }
}
