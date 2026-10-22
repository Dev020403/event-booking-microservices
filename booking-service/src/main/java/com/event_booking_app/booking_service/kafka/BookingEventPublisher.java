package com.event_booking_app.booking_service.kafka;

import com.event_booking_app.booking_service.kafka.event.BookingCancelledEvent;
import com.event_booking_app.booking_service.kafka.event.BookingConfirmedEvent;
import com.event_booking_app.booking_service.kafka.event.BookingCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingEventPublisher {

    public static final String TOPIC = "booking-service-events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishBookingCreated(BookingCreatedEvent event) {
        publish(event.getBookingId().toString(), event);
    }

    public void publishBookingConfirmed(BookingConfirmedEvent event) {
        publish(event.getBookingId().toString(), event);
    }

    public void publishBookingCancelled(BookingCancelledEvent event) {
        publish(event.getBookingId().toString(), event);
    }

    /**
     * Publishes the payload. When called inside a transaction, sending is deferred until
     * after commit so consumers never see events for changes that were rolled back.
     */
    public void publish(String key, Object payload) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(key, payload);
                }
            });
            return;
        }
        send(key, payload);
    }

    private void send(String key, Object payload) {
        kafkaTemplate.send(TOPIC, key, payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish booking event with key {} to {}", key, TOPIC, ex);
                    } else {
                        log.info("Published booking event with key {} to {}", key, TOPIC);
                    }
                });
    }
}
