package com.event_booking_app.ai_service.tool.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TicketAvailability(UUID ticketTypeId, String name, BigDecimal price, String currency,
                                 String salesStartAt, String salesEndAt, Integer availableQuantity) {
}
