package com.event_booking_app.booking_service.kafka;

import com.event_booking_app.booking_service.entity.BookingStatus;
import com.event_booking_app.booking_service.repository.BookingRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class InventoryEventListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventListener.class);

    private final BookingRepository bookingRepository;
    private final ObjectMapper objectMapper;

    public InventoryEventListener(BookingRepository bookingRepository, ObjectMapper objectMapper) {
        this.bookingRepository = bookingRepository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "inventory-service-events", groupId = "booking-service")
    public void onInventoryMessage(String rawJson) {
        try {
            JsonNode node = objectMapper.readTree(rawJson);

            // Handle hold release / expiry
            if (node.has("holdId")) {
                UUID holdId = UUID.fromString(node.get("holdId").asText());
                String reason = node.has("reason") ? node.get("reason").asText() : "";

                if ("expired".equalsIgnoreCase(reason)) {
                    bookingRepository.findByHoldId(holdId).ifPresent(booking -> {
                        if (booking.getStatus() == BookingStatus.HELD || booking.getStatus() == BookingStatus.PENDING) {
                            booking.setStatus(BookingStatus.EXPIRED);
                            bookingRepository.save(booking);
                            log.info("Booking {} marked as EXPIRED because inventory hold {} expired",
                                    booking.getId(), holdId);
                        }
                    });
                }
            }
        } catch (Exception ex) {
            log.error("Failed to process inventory event message: {}", rawJson, ex);
        }
    }
}
