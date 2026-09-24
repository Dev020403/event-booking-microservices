package com.event_booking_app.inventory_service.kafka.event;

import java.util.UUID;

public record InventoryReleasedEvent(
        UUID inventoryId,
        UUID holdId,
        int quantity,
        String reason
) {}