package com.event_booking_app.event_service.service;

import com.event_booking_app.event_service.dto.CreateEventRequest;
import com.event_booking_app.event_service.dto.EventResponse;
import com.event_booking_app.event_service.dto.UpdateEventRequest;
import com.event_booking_app.event_service.entity.EventCategory;
import com.event_booking_app.event_service.entity.EventStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.UUID;

public interface EventService {
    EventResponse createEvent(CreateEventRequest request, String currentUserId);
    Page<EventResponse> getEvents(EventCategory category, EventStatus status, String city, LocalDateTime fromDate, LocalDateTime toDate, Pageable pageable);
    EventResponse getEventById(UUID id);
    EventResponse updateEvent(UUID id, UpdateEventRequest request, String currentUserId, boolean isAdmin);
    EventResponse cancelEvent(UUID id, String currentUserId, boolean isAdmin);
}
