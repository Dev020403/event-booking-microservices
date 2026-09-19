package com.event_booking_app.event_service.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EventPublisher {
    private static final String TOPIC = "event-service-events";

    private final KafkaTemplate<String,Object> kafkaTemplate;

    public void publish(String key, Object payload){
        kafkaTemplate.send(TOPIC, key, payload)
                .whenComplete((result, ex)->{
                    if(ex != null)
                    {
                        log.error("Failed to publish event with key {}", key, ex);
                    }
                    else{
                        log.info("Published event with key {} to {}", key, TOPIC);
                    }
                });
    }
}
