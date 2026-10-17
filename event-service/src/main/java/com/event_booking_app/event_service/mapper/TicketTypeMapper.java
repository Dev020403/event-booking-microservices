package com.event_booking_app.event_service.mapper;

import com.event_booking_app.event_service.dto.CreateTicketTypeRequest;
import com.event_booking_app.event_service.dto.TicketTypeResponse;
import com.event_booking_app.event_service.entity.Event;
import com.event_booking_app.event_service.entity.TicketType;
import org.springframework.stereotype.Component;

@Component
public class TicketTypeMapper {

    public TicketType toEntity(CreateTicketTypeRequest request, Event event) {
        if (request == null) {
            return null;
        }
        return TicketType.builder()
                .event(event)
                .name(request.getName())
                .price(request.getPrice())
                .currency(request.getCurrency() != null ? request.getCurrency() : "INR")
                .description(request.getDescription())
                .salesStartAt(request.getSalesStartAt())
                .salesEndAt(request.getSalesEndAt())
                .build();
    }

    public TicketTypeResponse toResponse(TicketType ticketType) {
        if (ticketType == null) {
            return null;
        }
        return TicketTypeResponse.builder()
                .id(ticketType.getId())
                .eventId(ticketType.getEvent() != null ? ticketType.getEvent().getId() : null)
                .name(ticketType.getName())
                .price(ticketType.getPrice())
                .currency(ticketType.getCurrency())
                .description(ticketType.getDescription())
                .salesStartAt(ticketType.getSalesStartAt())
                .salesEndAt(ticketType.getSalesEndAt())
                .build();
    }
}
