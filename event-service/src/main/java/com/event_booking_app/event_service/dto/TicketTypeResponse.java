package com.event_booking_app.event_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketTypeResponse {
    private UUID id;
    private UUID eventId;
    private String name;
    private BigDecimal price;
    private String description;
    private LocalDateTime salesStartAt;
    private LocalDateTime salesEndAt;
}
