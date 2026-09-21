package com.event_booking_app.inventory_service.controller;

import com.event_booking_app.inventory_service.dto.HoldRequest;
import com.event_booking_app.inventory_service.dto.HoldResponse;
import com.event_booking_app.inventory_service.service.HoldService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class HoldController {

    private final HoldService holdService;

    // Called by Booking Service as step 2 of the Saga (or directly via
    // Postman for testing). Requires authentication — any authenticated
    // user/service, no specific role needed.
    @PostMapping("/api/v1/inventory/{inventoryId}/hold")
    public ResponseEntity<HoldResponse> holdInventory(
            @PathVariable UUID inventoryId,
            @Valid @RequestBody HoldRequest request
    ) {
        HoldResponse response = holdService.holdInventory(inventoryId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Called by Booking Service (step 4) once payment succeeds.
    @PostMapping("/api/v1/holds/{holdId}/confirm")
    public ResponseEntity<HoldResponse> confirmHold(@PathVariable UUID holdId) {
        HoldResponse response = holdService.confirmHold(holdId);
        return ResponseEntity.ok(response);
    }

    // Called by Booking Service on explicit cancellation or as a
    // compensating action if a later Saga step fails.
    @PostMapping("/api/v1/holds/{holdId}/release")
    public ResponseEntity<HoldResponse> releaseHold(@PathVariable UUID holdId) {
        HoldResponse response = holdService.releaseHold(holdId);
        return ResponseEntity.ok(response);
    }

    // Polling/debugging — check a hold's current status.
    @GetMapping("/api/v1/holds/{holdId}")
    public ResponseEntity<HoldResponse> getHold(@PathVariable UUID holdId) {
        HoldResponse response = holdService.getHold(holdId);
        return ResponseEntity.ok(response);
    }
}