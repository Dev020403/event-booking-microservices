package com.event_booking_app.inventory_service.mapper;

import com.event_booking_app.inventory_service.dto.CreateInventoryRequest;
import com.event_booking_app.inventory_service.dto.InventoryResponse;
import com.event_booking_app.inventory_service.entity.Inventory;
import org.springframework.stereotype.Component;

@Component
public class InventoryMapper {

    public InventoryResponse toResponse(Inventory inventory) {
        return InventoryResponse.builder()
                .id(inventory.getId())
                .eventId(inventory.getEventId())
                .ticketTypeId(inventory.getTicketTypeId())
                .totalQuantity(inventory.getTotalQuantity())
                .availableQuantity(inventory.getAvailableQuantity())
                .heldQuantity(inventory.getHeldQuantity())
                .bookedQuantity(inventory.getBookedQuantity())
                .createdAt(inventory.getCreatedAt())
                .updatedAt(inventory.getUpdatedAt())
                .build();
    }

    public Inventory toEntity(CreateInventoryRequest request) {
        return Inventory.builder()
                .eventId(request.getEventId())
                .ticketTypeId(request.getTicketTypeId())
                .totalQuantity(request.getTotalQuantity())
                .availableQuantity(request.getTotalQuantity())
                .heldQuantity(0)
                .bookedQuantity(0)
                .build();
    }
}