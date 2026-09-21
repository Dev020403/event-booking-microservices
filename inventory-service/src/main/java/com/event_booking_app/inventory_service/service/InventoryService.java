package com.event_booking_app.inventory_service.service;

import com.event_booking_app.inventory_service.dto.CreateInventoryRequest;
import com.event_booking_app.inventory_service.dto.InventoryResponse;

import java.util.UUID;

public interface InventoryService {
    InventoryResponse createInventory(CreateInventoryRequest request);
    InventoryResponse getInventory(UUID eventId, UUID ticketTypeId);
    InventoryResponse getInventoryById(UUID inventoryId);

}
