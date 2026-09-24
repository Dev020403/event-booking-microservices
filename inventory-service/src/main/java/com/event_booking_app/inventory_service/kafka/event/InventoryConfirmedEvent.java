package com.event_booking_app.inventory_service.kafka.event;

import java.util.UUID;

public record InventoryConfirmedEvent(
        UUID inventoryId,
        UUID holdId,
        int quantity
) {}