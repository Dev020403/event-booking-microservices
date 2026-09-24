package com.event_booking_app.inventory_service.service;

import com.event_booking_app.inventory_service.dto.HoldRequest;
import com.event_booking_app.inventory_service.dto.HoldResponse;

import java.util.UUID;

public interface HoldService {
    HoldResponse holdInventory(UUID inventoryId, HoldRequest request);

    HoldResponse confirmHold(UUID holdId);

    HoldResponse releaseHold(UUID holdId);

    HoldResponse getHold(UUID holdId);

    void expireHold(UUID holdId);
}
