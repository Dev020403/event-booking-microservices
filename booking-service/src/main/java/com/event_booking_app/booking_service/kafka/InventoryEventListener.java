package com.event_booking_app.booking_service.kafka;

import com.event_booking_app.booking_service.entity.BookingStatus;
import com.event_booking_app.booking_service.kafka.event.BookingExpiredEvent;
import com.event_booking_app.booking_service.repository.BookingRepository;
import com.event_booking_app.booking_service.service.BookingTransactionSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class InventoryEventListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventListener.class);

    private final BookingRepository bookingRepository;
    private final BookingTransactionSupport txSupport;
    private final BookingEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public InventoryEventListener(BookingRepository bookingRepository, BookingTransactionSupport txSupport,
                                  BookingEventPublisher eventPublisher, ObjectMapper objectMapper) {
        this.bookingRepository = bookingRepository;
        this.txSupport = txSupport;
        this.eventPublisher = eventPublisher;
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
                    // Only HELD bookings expire here. A PENDING booking is mid-payment: that
                    // request will fail to confirm the hold and refund + mark PAYMENT_FAILED
                    // itself. The compare-and-set also avoids overwriting a concurrent change.
                    bookingRepository.findByHoldId(holdId).ifPresent(booking -> {
                        if (txSupport.transitionStatus(booking.getId(), BookingStatus.HELD, BookingStatus.EXPIRED)) {
                            log.info("Booking {} marked as EXPIRED because inventory hold {} expired",
                                    booking.getId(), holdId);
                            eventPublisher.publishBookingExpired(BookingExpiredEvent.builder()
                                    .bookingId(booking.getId())
                                    .userId(booking.getUserId())
                                    .userEmail(booking.getUserEmail())
                                    .eventId(booking.getEventId())
                                    .ticketTypeId(booking.getTicketTypeId())
                                    .quantity(booking.getQuantity())
                                    .expiredAt(Instant.now())
                                    .build());
                        }
                    });
                }
            }
        } catch (Exception ex) {
            log.error("Failed to process inventory event message: {}", rawJson, ex);
        }
    }
}
