package com.event_booking_app.inventory_service.controller;

import com.event_booking_app.inventory_service.dto.CreateInventoryRequest;
import com.event_booking_app.inventory_service.dto.InventoryResponse;
import com.event_booking_app.inventory_service.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    // ADMIN-only manual creation — the primary path is the Kafka listener
    // reacting to TicketTypeCreatedEvent, but this exists as a fallback/
    // override and is useful for testing before that listener is wired up.
    // Restricted to ADMIN at the SecurityConfig level (not checked here).
    @PostMapping
    public ResponseEntity<InventoryResponse> createInventory(
            @Valid @RequestBody CreateInventoryRequest request
    ) {
        InventoryResponse response = inventoryService.createInventory(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Public — shows current availability, e.g. for a frontend's
    // "X tickets left" display, and used by Booking Service before
    // attempting a hold.
    @GetMapping("/{eventId}/{ticketTypeId}")
    public ResponseEntity<InventoryResponse> getInventory(
            @PathVariable UUID eventId,
            @PathVariable UUID ticketTypeId
    ) {
        InventoryResponse response = inventoryService.getInventory(eventId, ticketTypeId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{inventoryId}")
    public ResponseEntity<InventoryResponse> getInventoryById(@PathVariable UUID inventoryId) {
        InventoryResponse response = inventoryService.getInventoryById(inventoryId);
        return ResponseEntity.ok(response);
    }
}