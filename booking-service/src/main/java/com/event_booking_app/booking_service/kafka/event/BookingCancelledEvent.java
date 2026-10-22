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
    public static final String TYPE = "BOOKING_CANCELLED";

    // Explicit type marker so consumers don't depend on this service's class names
    @Builder.Default
    private String eventType = TYPE;

    private UUID bookingId;
    private UUID userId;
    private String userEmail;
    private UUID eventId;
    private UUID ticketTypeId;
    private Integer quantity;
    // True when a confirmed (paid) booking was cancelled and a refund was issued
    private boolean refunded;
    private Instant cancelledAt;
}
