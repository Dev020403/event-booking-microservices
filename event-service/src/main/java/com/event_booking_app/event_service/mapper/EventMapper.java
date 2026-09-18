package com.event_booking_app.event_service.mapper;

import com.event_booking_app.event_service.dto.CreateEventRequest;
import com.event_booking_app.event_service.dto.EventResponse;
import com.event_booking_app.event_service.entity.Event;
import com.event_booking_app.event_service.entity.EventStatus;
import com.event_booking_app.event_service.entity.Venue;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class EventMapper {

    private final VenueMapper venueMapper;

    public Event toEntity(CreateEventRequest request, Venue venue, UUID organizerId) {
        if (request == null) {
            return null;
        }
        EventStatus status = request.getStatus() != null ? request.getStatus() : EventStatus.DRAFT;

        return Event.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .category(request.getCategory())
                .status(status)
                .startDateTime(request.getStartDateTime())
                .endDateTime(request.getEndDateTime())
                .organizerId(organizerId)
                .venue(venue)
                .build();
    }

    public EventResponse toResponse(Event event) {
        if (event == null) {
            return null;
        }
        return EventResponse.builder()
                .id(event.getId())
                .title(event.getTitle())
                .description(event.getDescription())
                .category(event.getCategory())
                .status(event.getStatus())
                .startDateTime(event.getStartDateTime())
                .endDateTime(event.getEndDateTime())
                .organizerId(event.getOrganizerId())
                .venue(venueMapper.toResponse(event.getVenue()))
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .build();
    }
}
