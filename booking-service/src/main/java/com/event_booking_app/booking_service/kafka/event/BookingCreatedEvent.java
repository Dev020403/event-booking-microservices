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
    private UUID bookingId;
    private UUID userId;
    private UUID eventId;
    private UUID ticketTypeId;
    private Integer quantity;
    private BigDecimal totalAmount;
    private String status;
    private Instant createdAt;
}
