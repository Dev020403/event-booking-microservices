package com.event_booking_app.ai_service.tool.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MyBookingsResult(List<BookingSummary> bookings, boolean hasMore, String note) {

    public static MyBookingsResult of(List<BookingSummary> bookings, boolean hasMore) {
        return new MyBookingsResult(bookings, hasMore, bookings.isEmpty() ? "The user has no bookings." : null);
    }

    public static MyBookingsResult unavailable(String note) {
        return new MyBookingsResult(List.of(), false, note);
    }
}