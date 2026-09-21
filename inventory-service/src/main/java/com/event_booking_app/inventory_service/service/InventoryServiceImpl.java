package com.event_booking_app.inventory_service.service;

import com.event_booking_app.inventory_service.dto.CreateInventoryRequest;
import com.event_booking_app.inventory_service.dto.InventoryResponse;
import com.event_booking_app.inventory_service.entity.Inventory;
import com.event_booking_app.inventory_service.exception.InventoryAlreadyExistsException;
import com.event_booking_app.inventory_service.exception.InventoryNotFoundException;
import com.event_booking_app.inventory_service.mapper.InventoryMapper;
import com.event_booking_app.inventory_service.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryServiceImpl implements InventoryService{

    private final InventoryRepository inventoryRepository;
    private final InventoryMapper inventoryMapper;

    @Override
    public InventoryResponse createInventory(CreateInventoryRequest request) {
        if(inventoryRepository.existsByEventIdAndTicketTypeId(request.getEventId(), request.getTicketTypeId()))
        {
            throw new InventoryAlreadyExistsException( request.getEventId(), request.getTicketTypeId());
        }
        Inventory inventory = inventoryMapper.toEntity(request);
        Inventory saved = inventoryRepository.save(inventory);

        log.info("Created inventory {} for eventId={}, ticketTypeId={}, totalQuantity={}",
                saved.getId(), saved.getEventId(), saved.getTicketTypeId(), saved.getTotalQuantity());

        return inventoryMapper.toResponse(saved);
    }

    @Override
    public InventoryResponse getInventory(UUID eventId, UUID ticketTypeId) {
        Inventory inventory = inventoryRepository.findByEventIdAndTicketTypeId(eventId, ticketTypeId)
                .orElseThrow(() -> new InventoryNotFoundException(eventId, ticketTypeId));
        return inventoryMapper.toResponse(inventory);
    }

    @Override
    public InventoryResponse getInventoryById(UUID inventoryId) {
        Inventory inventory = inventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new InventoryNotFoundException(inventoryId));
        return inventoryMapper.toResponse(inventory);
    }
}
