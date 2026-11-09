package com.event_booking_app.ai_service.client;

import com.event_booking_app.ai_service.client.dto.InventoryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "inventory-service")
public interface InventoryClient {

    @GetMapping("/api/v1/inventory/{eventId}/{ticketTypeId}")
    InventoryResponse getInventory(@PathVariable("eventId") UUID eventId,
                                   @PathVariable("ticketTypeId") UUID ticketTypeId);
}
