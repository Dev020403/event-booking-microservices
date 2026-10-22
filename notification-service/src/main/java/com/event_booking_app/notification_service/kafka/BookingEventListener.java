package com.event_booking_app.notification_service.kafka;

import com.event_booking_app.notification_service.entity.EmailStatus;
import com.event_booking_app.notification_service.service.BookingNotificationFactory;
import com.event_booking_app.notification_service.service.EmailDeliveryService;
import com.event_booking_app.notification_service.service.NotificationDraft;
import com.event_booking_app.notification_service.service.NotificationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
public class BookingEventListener {

    public static final String TOPIC = "booking-service-events";

    private final BookingNotificationFactory notificationFactory;
    private final NotificationService notificationService;
    private final EmailDeliveryService emailDeliveryService;
    // Plain Jackson 2 mapper for tree parsing, independent of the web layer's mapper.
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BookingEventListener(BookingNotificationFactory notificationFactory,
                                NotificationService notificationService,
                                EmailDeliveryService emailDeliveryService) {
        this.notificationFactory = notificationFactory;
        this.notificationService = notificationService;
        this.emailDeliveryService = emailDeliveryService;
    }

    @KafkaListener(topics = TOPIC, groupId = "notification-service")
    public void onBookingEvent(String rawJson) {
        Optional<NotificationDraft> draft;
        try {
            JsonNode event = objectMapper.readTree(rawJson);
            draft = notificationFactory.fromBookingEvent(event);
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            // Malformed messages will never succeed — log and skip instead of retrying.
            log.error("Skipping unprocessable booking event: {}", ex.getMessage());
            return;
        }

        // DB failures propagate so the container's error handler retries the record.
        draft.flatMap(notificationService::record)
                .filter(notification -> notification.getEmailStatus() == EmailStatus.PENDING)
                // Send right away for low latency; failures are recorded and retried by the
                // scheduler, so they never fail (or re-deliver) the Kafka record.
                .ifPresent(notification -> emailDeliveryService.deliver(notification.getId()));
    }
}
