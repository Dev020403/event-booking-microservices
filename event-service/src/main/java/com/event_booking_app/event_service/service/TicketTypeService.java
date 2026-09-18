package com.event_booking_app.event_service.service;

import com.event_booking_app.event_service.dto.CreateTicketTypeRequest;
import com.event_booking_app.event_service.dto.TicketTypeResponse;
import com.event_booking_app.event_service.dto.UpdateTicketTypeRequest;

import java.util.List;
import java.util.UUID;

public interface TicketTypeService {
    TicketTypeResponse createTicketType(UUID eventId, CreateTicketTypeRequest request, String currentUserId, boolean isAdmin);
    List<TicketTypeResponse> getTicketTypesByEvent(UUID eventId);
    TicketTypeResponse updateTicketType(UUID ticketTypeId, UpdateTicketTypeRequest request, String currentUserId, boolean isAdmin);
}
