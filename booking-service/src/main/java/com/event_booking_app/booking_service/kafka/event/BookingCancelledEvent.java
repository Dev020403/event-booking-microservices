package com.event_booking_app.booking_service.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingCancelledEvent {
    private UUID bookingId;
    private UUID userId;
    private UUID eventId;
    private UUID ticketTypeId;
    private Integer quantity;
    private Instant cancelledAt;
}
