package com.event_booking_app.event_service.service;

import com.event_booking_app.event_service.dto.CreateEventRequest;
import com.event_booking_app.event_service.dto.EventResponse;
import com.event_booking_app.event_service.dto.UpdateEventRequest;
import com.event_booking_app.event_service.entity.Event;
import com.event_booking_app.event_service.entity.EventCategory;
import com.event_booking_app.event_service.entity.EventStatus;
import com.event_booking_app.event_service.entity.Venue;
import com.event_booking_app.event_service.exception.EventNotFoundException;
import com.event_booking_app.event_service.exception.UnauthorizedEventAccessException;
import com.event_booking_app.event_service.exception.VenueNotFoundException;
import com.event_booking_app.event_service.kafka.EventPublisher;
import com.event_booking_app.event_service.kafka.event.EventCancelledEvent;
import com.event_booking_app.event_service.kafka.event.EventCreateEvent;
import com.event_booking_app.event_service.mapper.EventMapper;
import com.event_booking_app.event_service.repository.EventRepository;
import com.event_booking_app.event_service.repository.EventSpecification;
import com.event_booking_app.event_service.repository.VenueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final VenueRepository venueRepository;
    private final EventMapper eventMapper;
    private final EventPublisher eventPublisher;

    @Override
    @Transactional
    public EventResponse createEvent(CreateEventRequest request, String currentUserId) {
        log.info("Creating event '{}' for organizerId {}", request.getTitle(), currentUserId);

        validateDateRange(request.getStartDateTime(), request.getEndDateTime());

        Venue venue = venueRepository.findById(request.getVenueId())
                .orElseThrow(() -> new VenueNotFoundException(request.getVenueId()));

        UUID organizerUuid = UUID.fromString(currentUserId);
        Event event = eventMapper.toEntity(request, venue, organizerUuid);
        Event savedEvent = eventRepository.save(event);
        log.info("Event created successfully with ID: {}", savedEvent.getId());

        // Publish event to Kafka using the persisted ID
        eventPublisher.publish(
                savedEvent.getId().toString(),
                new EventCreateEvent(savedEvent.getId(), savedEvent.getOrganizerId(), savedEvent.getTitle())
        );

        return eventMapper.toResponse(savedEvent);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EventResponse> getEvents(
            EventCategory category,
            EventStatus status,
            String city,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            Pageable pageable
    ) {
        log.debug("Searching events with category={}, status={}, city={}, fromDate={}, toDate={}",
                category, status, city, fromDate, toDate);

        return eventRepository.findAll(
                EventSpecification.withFilters(category, status, city, fromDate, toDate),
                pageable
        ).map(eventMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public EventResponse getEventById(UUID id) {
        log.debug("Fetching event details for ID: {}", id);
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new EventNotFoundException(id));
        return eventMapper.toResponse(event);
    }

    @Override
    @Transactional
    public EventResponse updateEvent(UUID id, UpdateEventRequest request, String currentUserId, boolean isAdmin) {
        log.info("Updating event {}", id);

        validateDateRange(request.getStartDateTime(), request.getEndDateTime());

        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new EventNotFoundException(id));

        validateOwnership(event, currentUserId, isAdmin, "Only the event organizer or an ADMIN can update this event");

        if (!event.getVenue().getId().equals(request.getVenueId())) {
            Venue newVenue = venueRepository.findById(request.getVenueId())
                    .orElseThrow(() -> new VenueNotFoundException(request.getVenueId()));
            event.setVenue(newVenue);
        }

        event.setTitle(request.getTitle());
        event.setDescription(request.getDescription());
        event.setCategory(request.getCategory());
        if (request.getStatus() != null) {
            event.setStatus(request.getStatus());
        }
        event.setStartDateTime(request.getStartDateTime());
        event.setEndDateTime(request.getEndDateTime());

        Event updatedEvent = eventRepository.save(event);
        log.info("Event updated successfully: {}", updatedEvent.getId());

        return eventMapper.toResponse(updatedEvent);
    }

    @Override
    @Transactional
    public EventResponse cancelEvent(UUID id, String currentUserId, boolean isAdmin) {
        log.info("Cancelling event {}", id);
        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new EventNotFoundException(id));

        validateOwnership(event, currentUserId, isAdmin, "Only the event organizer or an ADMIN can cancel this event");

        if (event.getStatus() == EventStatus.CANCELLED) {
            log.info("Event {} is already cancelled, returning current state", id);
            return eventMapper.toResponse(event);
        }

        event.setStatus(EventStatus.CANCELLED);
        Event cancelledEvent = eventRepository.save(event);
        log.info("Event cancelled successfully: {}", cancelledEvent.getId());

        // Publish cancel event to Kafka
        eventPublisher.publish(
                cancelledEvent.getId().toString(),
                new EventCancelledEvent(cancelledEvent.getId())
        );

        return eventMapper.toResponse(cancelledEvent);
    }

    private void validateOwnership(Event event, String currentUserId, boolean isAdmin, String errorMessage) {
        if (isAdmin) {
            return;
        }
        if (!event.getOrganizerId().toString().equalsIgnoreCase(currentUserId)) {
            throw new UnauthorizedEventAccessException(errorMessage);
        }
    }

    private void validateDateRange(LocalDateTime startDateTime, LocalDateTime endDateTime) {
        if (endDateTime != null && startDateTime != null && !endDateTime.isAfter(startDateTime)) {
            throw new IllegalArgumentException("End date/time must be after start date/time");
        }
    }
}
