package com.event_booking_app.ai_service.client;

import com.event_booking_app.ai_service.client.dto.EventCategory;
import com.event_booking_app.ai_service.client.dto.EventPage;
import com.event_booking_app.ai_service.client.dto.TicketTypeResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "event-service")
public interface EventClient {
    @GetMapping("/api/v1/events")
    EventPage searchEvents(@RequestParam(value = "category", required = false) EventCategory category,
                           @RequestParam(value = "city", required = false) String city,
                           @RequestParam(value = "fromDate", required = false) String fromDate,
                           @RequestParam(value = "toDate", required = false) String toDate,
                           @RequestParam("size") int size);

    @GetMapping("/api/v1/events/{id}/ticket-types")
    List<TicketTypeResponse> getTicketTypes(@PathVariable("id") UUID eventId);
}
