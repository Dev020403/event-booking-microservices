package com.event_booking_app.booking_service.client;

import com.event_booking_app.booking_service.client.dto.EventResponse;
import com.event_booking_app.booking_service.client.dto.TicketTypeResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "event-service", configuration = FeignClientInterceptor.class)
public interface EventClient {

    @GetMapping("/api/v1/events/{id}")
    EventResponse getEventById(@PathVariable("id") UUID id);

    @GetMapping("/api/v1/ticket-types/{id}")
    TicketTypeResponse getTicketTypeById(@PathVariable("id") UUID id);
}
