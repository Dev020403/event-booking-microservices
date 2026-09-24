package com.event_booking_app.event_service.controller;

import com.event_booking_app.event_service.dto.CreateTicketTypeRequest;
import com.event_booking_app.event_service.dto.TicketTypeResponse;
import com.event_booking_app.event_service.dto.UpdateTicketTypeRequest;
import com.event_booking_app.event_service.service.TicketTypeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class TicketTypeController {

    private final TicketTypeService ticketTypeService;

    @PostMapping("/api/v1/events/{id}/ticket-types")
    public ResponseEntity<TicketTypeResponse> createTicketType(
            @PathVariable("id") UUID eventId,
            @Valid @RequestBody CreateTicketTypeRequest request,
            Authentication authentication
    ) {
        String currentUserId = authentication.getName();
        boolean isAdmin = SecurityUtils.hasRoleAdmin(authentication);
        TicketTypeResponse response = ticketTypeService.createTicketType(eventId, request, currentUserId, isAdmin);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/api/v1/events/{id}/ticket-types")
    public ResponseEntity<List<TicketTypeResponse>> getTicketTypesByEvent(@PathVariable("id") UUID eventId) {
        return ResponseEntity.ok(ticketTypeService.getTicketTypesByEvent(eventId));
    }

    @PutMapping("/api/v1/ticket-types/{id}")
    public ResponseEntity<TicketTypeResponse> updateTicketType(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateTicketTypeRequest request,
            Authentication authentication
    ) {
        String currentUserId = authentication.getName();
        boolean isAdmin = SecurityUtils.hasRoleAdmin(authentication);
        TicketTypeResponse response = ticketTypeService.updateTicketType(id, request, currentUserId, isAdmin);
        return ResponseEntity.ok(response);
    }
}
