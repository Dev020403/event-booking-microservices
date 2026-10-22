package com.event_booking_app.inventory_service.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryEventPublisher {

    private static final String TOPIC = "inventory-service-events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Publishes the payload. When called inside a transaction, sending is deferred until
     * after commit so consumers never see events for changes that were rolled back
     * (e.g. an optimistic-lock failure that is then retried).
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
                        log.error("Failed to publish event with key {} to {}", key, TOPIC, ex);
                    } else {
                        log.info("Published event with key {} to {}", key, TOPIC);
                    }
                });
    }
}
