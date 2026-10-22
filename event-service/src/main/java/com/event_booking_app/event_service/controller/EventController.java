package com.event_booking_app.event_service.controller;

import com.event_booking_app.event_service.dto.CreateEventRequest;
import com.event_booking_app.event_service.dto.EventResponse;
import com.event_booking_app.event_service.dto.UpdateEventRequest;
import com.event_booking_app.event_service.entity.EventCategory;
import com.event_booking_app.event_service.entity.EventStatus;
import com.event_booking_app.event_service.service.EventService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private static final int MAX_PAGE_SIZE = 100;

    private final EventService eventService;

    @PostMapping
    public ResponseEntity<EventResponse> createEvent(@Valid @RequestBody CreateEventRequest request, Authentication authentication) {
        String currentUserId = authentication.getName();
        EventResponse response = eventService.createEvent(request, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<EventResponse>> getEvents(@RequestParam(required = false) EventCategory category, @RequestParam(required = false) String city, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate, @PageableDefault(size = 20) Pageable pageable) {
        Page<EventResponse> response = eventService.getEvents(category, city, fromDate, toDate, sanitizePageable(pageable));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/manage")
    public ResponseEntity<Page<EventResponse>> getManagedEvents(@RequestParam(required = false) EventCategory category, @RequestParam(required = false) EventStatus status, @RequestParam(required = false) String city, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate, @PageableDefault(size = 20) Pageable pageable, Authentication authentication) {
        String currentUserId = authentication.getName();
        boolean isAdmin = SecurityUtils.hasRoleAdmin(authentication);
        Page<EventResponse> response = eventService.getManagedEvents(category, status, city, fromDate, toDate, currentUserId, isAdmin, sanitizePageable(pageable));
        return ResponseEntity.ok(response);
    }

    private Pageable sanitizePageable(Pageable pageable) {
        if (pageable != null && pageable.getPageSize() > MAX_PAGE_SIZE) {
            return PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort());
        }
        return pageable;
    }

    @GetMapping("/manage/{id}")
    public ResponseEntity<EventResponse> getManagedEventById(@PathVariable UUID id, Authentication authentication) {
        String currentUserId = authentication.getName();
        boolean isAdmin = SecurityUtils.hasRoleAdmin(authentication);
        EventResponse response = eventService.getManagedEventById(id, currentUserId, isAdmin);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventResponse> getEventById(@PathVariable UUID id) {
        EventResponse response = eventService.getEventById(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<EventResponse> publishEvent(@PathVariable UUID id, Authentication authentication) {
        String currentUserId = authentication.getName();
        boolean isAdmin = SecurityUtils.hasRoleAdmin(authentication);
        EventResponse response = eventService.publishEvent(id, currentUserId, isAdmin);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<EventResponse> updateEvent(@PathVariable UUID id, @Valid @RequestBody UpdateEventRequest request, Authentication authentication) {
        String currentUserId = authentication.getName();
        boolean isAdmin = SecurityUtils.hasRoleAdmin(authentication);
        EventResponse response = eventService.updateEvent(id, request, currentUserId, isAdmin);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<EventResponse> cancelEvent(@PathVariable UUID id, Authentication authentication) {
        String currentUserId = authentication.getName();
        boolean isAdmin = SecurityUtils.hasRoleAdmin(authentication);
        EventResponse response = eventService.cancelEvent(id, currentUserId, isAdmin);
        return ResponseEntity.ok(response);
    }
}
