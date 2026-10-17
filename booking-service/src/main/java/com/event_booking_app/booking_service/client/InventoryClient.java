package com.event_booking_app.booking_service.client;

import com.event_booking_app.booking_service.client.dto.HoldRequest;
import com.event_booking_app.booking_service.client.dto.HoldResponse;
import com.event_booking_app.booking_service.client.dto.InventoryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(name = "inventory-service", configuration = FeignClientInterceptor.class)
public interface InventoryClient {

    @GetMapping("/api/v1/inventory/{eventId}/{ticketTypeId}")
    InventoryResponse getInventory(
            @PathVariable("eventId") UUID eventId,
            @PathVariable("ticketTypeId") UUID ticketTypeId
    );

    @PostMapping("/api/v1/inventory/{inventoryId}/hold")
    HoldResponse holdInventory(
            @PathVariable("inventoryId") UUID inventoryId,
            @RequestBody HoldRequest request
    );

    @PostMapping("/api/v1/holds/{holdId}/confirm")
    HoldResponse confirmHold(@PathVariable("holdId") UUID holdId);

    @PostMapping("/api/v1/holds/{holdId}/release")
    HoldResponse releaseHold(@PathVariable("holdId") UUID holdId);

    @GetMapping("/api/v1/holds/{holdId}")
    HoldResponse getHold(@PathVariable("holdId") UUID holdId);
}
