package com.event_booking_app.ai_service.tool.mapper;

import com.event_booking_app.ai_service.client.dto.BookingResponse;
import com.event_booking_app.ai_service.tool.dto.BookingSummary;
import org.springframework.stereotype.Component;

@Component
public class BookingToolMapper {

    public BookingSummary toSummary(BookingResponse booking, String eventTitle) {
        return new BookingSummary(booking.id(), booking.eventId(), eventTitle, booking.status(),
                booking.quantity(), booking.totalAmount(), booking.currency(), booking.createdAt());
    }
}
