package com.event_booking_app.inventory_service.kafka.event;

import java.util.UUID;

public record InventoryHeldEvent(
        UUID inventoryId,
        UUID eventId,
        UUID ticketTypeId,
        UUID holdId,
        int quantity
) {}