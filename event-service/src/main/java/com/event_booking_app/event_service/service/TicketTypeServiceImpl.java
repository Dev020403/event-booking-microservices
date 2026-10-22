package com.event_booking_app.event_service.service;

import com.event_booking_app.event_service.dto.CreateTicketTypeRequest;
import com.event_booking_app.event_service.dto.TicketTypeResponse;
import com.event_booking_app.event_service.dto.UpdateTicketTypeRequest;
import com.event_booking_app.event_service.entity.Event;
import com.event_booking_app.event_service.entity.EventStatus;
import com.event_booking_app.event_service.entity.TicketType;
import com.event_booking_app.event_service.exception.EventNotFoundException;
import com.event_booking_app.event_service.exception.TicketTypeNotFoundException;
import com.event_booking_app.event_service.exception.UnauthorizedEventAccessException;
import com.event_booking_app.event_service.kafka.EventPublisher;
import com.event_booking_app.event_service.kafka.event.TicketTypeCreatedEvent;
import com.event_booking_app.event_service.mapper.TicketTypeMapper;
import com.event_booking_app.event_service.repository.EventRepository;
import com.event_booking_app.event_service.repository.TicketTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketTypeServiceImpl implements TicketTypeService {

    private final TicketTypeRepository ticketTypeRepository;
    private final EventRepository eventRepository;
    private final TicketTypeMapper ticketTypeMapper;
    private final EventPublisher eventPublisher;

    @Override
    @Transactional
    public TicketTypeResponse createTicketType(UUID eventId, CreateTicketTypeRequest request, String currentUserId, boolean isAdmin) {
        log.info("Creating ticket type '{}' for event {}", request.getName(), eventId);
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));

        validateOwnership(event, currentUserId, isAdmin, "Only the event organizer or an ADMIN can add ticket types to this event");

        if (event.getStatus() == EventStatus.CANCELLED || event.getStatus() == EventStatus.COMPLETED) {
            throw new IllegalStateException("Cannot add ticket types to an event that is " + event.getStatus());
        }

        validateCurrency(request.getCurrency());
        validateSalesDates(request.getSalesStartAt(), request.getSalesEndAt(), event.getStartDateTime());

        TicketType ticketType = ticketTypeMapper.toEntity(request, event);
        ticketType.setCurrency(request.getCurrency().toUpperCase());
        TicketType savedTicketType = ticketTypeRepository.save(ticketType);
        log.info("Ticket type created successfully with ID: {}", savedTicketType.getId());

        eventPublisher.publish(
                event.getId().toString(),
                new TicketTypeCreatedEvent(eventId, savedTicketType.getId(), savedTicketType.getName())
        );

        return ticketTypeMapper.toResponse(savedTicketType);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketTypeResponse> getTicketTypesByEvent(UUID eventId) {
        log.debug("Fetching ticket types for event {}", eventId);
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));

        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new EventNotFoundException(eventId);
        }

        return ticketTypeRepository.findByEventId(eventId).stream()
                .map(ticketTypeMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TicketTypeResponse> getManagedTicketTypesByEvent(UUID eventId, String currentUserId, boolean isAdmin) {
        log.debug("Fetching managed ticket types for event {}", eventId);
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));

        validateOwnership(event, currentUserId, isAdmin, "Only the event organizer or an ADMIN can view ticket types for this event");

        return ticketTypeRepository.findByEventId(eventId).stream()
                .map(ticketTypeMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public TicketTypeResponse getTicketTypeById(UUID ticketTypeId) {
        log.debug("Fetching ticket type details for ID: {}", ticketTypeId);
        TicketType ticketType = ticketTypeRepository.findById(ticketTypeId)
                .orElseThrow(() -> new TicketTypeNotFoundException(ticketTypeId));

        // Public API must not expose ticket types for unpublished events.
        if (ticketType.getEvent().getStatus() != EventStatus.PUBLISHED) {
            throw new TicketTypeNotFoundException(ticketTypeId);
        }
        return ticketTypeMapper.toResponse(ticketType);
    }

    @Override
    @Transactional
    public TicketTypeResponse updateTicketType(UUID ticketTypeId, UpdateTicketTypeRequest request, String currentUserId, boolean isAdmin) {
        log.info("Updating ticket type {}", ticketTypeId);
        TicketType ticketType = ticketTypeRepository.findById(ticketTypeId)
                .orElseThrow(() -> new TicketTypeNotFoundException(ticketTypeId));

        Event event = ticketType.getEvent();
        validateOwnership(event, currentUserId, isAdmin, "Only the event organizer or an ADMIN can update ticket types for this event");

        if (event.getStatus() == EventStatus.CANCELLED || event.getStatus() == EventStatus.COMPLETED) {
            throw new IllegalStateException("Cannot update ticket types for an event that is " + event.getStatus());
        }

        validateCurrency(request.getCurrency());
        validateSalesDates(request.getSalesStartAt(), request.getSalesEndAt(), event.getStartDateTime());

        ticketType.setName(request.getName());
        ticketType.setPrice(request.getPrice());
        ticketType.setCurrency(request.getCurrency().toUpperCase());
        ticketType.setDescription(request.getDescription());
        ticketType.setSalesStartAt(request.getSalesStartAt());
        ticketType.setSalesEndAt(request.getSalesEndAt());

        TicketType updatedTicketType = ticketTypeRepository.save(ticketType);
        log.info("Ticket type updated successfully: {}", updatedTicketType.getId());

        return ticketTypeMapper.toResponse(updatedTicketType);
    }

    private void validateOwnership(Event event, String currentUserId, boolean isAdmin, String errorMessage) {
        if (isAdmin) {
            return;
        }
        if (!event.getOrganizerId().toString().equalsIgnoreCase(currentUserId)) {
            throw new UnauthorizedEventAccessException(errorMessage);
        }
    }

    private void validateCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Currency cannot be empty");
        }
        try {
            Currency.getInstance(currency.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid ISO 4217 currency code: " + currency);
        }
    }

    private void validateSalesDates(LocalDateTime salesStartAt, LocalDateTime salesEndAt, LocalDateTime eventStartDateTime) {
        if (salesStartAt == null || salesEndAt == null) {
            throw new IllegalArgumentException("Sales start and end date/time are required");
        }
        if (!salesEndAt.isAfter(salesStartAt)) {
            throw new IllegalArgumentException("Sales end date/time must be after sales start date/time");
        }
        if (eventStartDateTime != null && salesEndAt.isAfter(eventStartDateTime)) {
            throw new IllegalArgumentException("Ticket sales must end before or when the event starts");
        }
    }
}
