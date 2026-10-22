package com.event_booking_app.booking_service.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingCreatedEvent {
    public static final String TYPE = "BOOKING_CREATED";

    // Explicit type marker so consumers don't depend on this service's class names
    @Builder.Default
    private String eventType = TYPE;

    private UUID bookingId;
    private UUID userId;
    private String userEmail;
    private UUID eventId;
    private UUID ticketTypeId;
    private Integer quantity;
    private BigDecimal totalAmount;
    private String currency;
    private String status;
    private Instant createdAt;
}
