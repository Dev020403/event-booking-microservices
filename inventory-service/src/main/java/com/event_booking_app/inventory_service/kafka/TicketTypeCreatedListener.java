package com.event_booking_app.inventory_service.kafka;

import com.event_booking_app.inventory_service.dto.CreateInventoryRequest;
import com.event_booking_app.inventory_service.exception.InventoryAlreadyExistsException;
import com.event_booking_app.inventory_service.service.InventoryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
public class TicketTypeCreatedListener {
    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;

    @Value("${inventory.default-initial-quantity:0}")
    private int defaultInitialQuantity;

    public TicketTypeCreatedListener(InventoryService inventoryService, ObjectMapper objectMapper) {
        this.inventoryService = inventoryService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "event-service-events", groupId = "inventory-service")
    public void onMessage(String rawJson) {
        try {
            JsonNode node = objectMapper.readTree(rawJson);

            boolean looksLikeTicketTypeCreated = node.has("ticketTypeId") && node.has("name")
                    && !node.has("title") && !node.has("status");

            if (!looksLikeTicketTypeCreated) {
                return;
            }
            UUID eventId = UUID.fromString(node.get("eventId").asText());
            UUID ticketTypeId = UUID.fromString(node.get("ticketTypeId").asText());

            CreateInventoryRequest request = CreateInventoryRequest.builder()
                    .eventId(eventId)
                    .ticketTypeId(ticketTypeId)
                    .totalQuantity(defaultInitialQuantity)
                    .build();

            inventoryService.createInventory(request);
            log.info("Auto-created inventory for eventId={}, ticketTypeId={}, quantity={} from Kafka event",
                    eventId, ticketTypeId, defaultInitialQuantity);
        } catch (InventoryAlreadyExistsException ex) {
            // Expected on consumer restart/rebalance replay — not an error
            log.info("Inventory already exists for this ticket type, skipping (likely a replayed message)");
        } catch (Exception ex) {
            // Don't let a malformed message crash the listener thread —
            // log and move on. A dead-letter topic would be the production
            // fix; out of scope here.
            log.error("Failed to process message from event-service-events: {}", rawJson, ex);
        }
    }
}
