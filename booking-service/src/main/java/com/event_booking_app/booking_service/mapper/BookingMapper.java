package com.event_booking_app.booking_service.mapper;
import com.event_booking_app.booking_service.dto.BookingResponse;
import com.event_booking_app.booking_service.entity.Booking;
import org.springframework.stereotype.Component;
@Component
public class BookingMapper {
    public BookingResponse toResponse(Booking booking) {
        if (booking == null) {
            return null;
        }
        return BookingResponse.builder()
                .id(booking.getId())
                .userId(booking.getUserId())
                .eventId(booking.getEventId())
                .ticketTypeId(booking.getTicketTypeId())
                .inventoryId(booking.getInventoryId())
                .holdId(booking.getHoldId())
                .quantity(booking.getQuantity())
                .unitPrice(booking.getUnitPrice())
                .totalAmount(booking.getTotalAmount())
                .status(booking.getStatus())
                .paymentId(booking.getPaymentId())
                .idempotencyKey(booking.getIdempotencyKey())
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .build();
    }
}
