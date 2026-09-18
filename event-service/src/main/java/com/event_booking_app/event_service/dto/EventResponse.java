package com.event_booking_app.event_service.dto;

import com.event_booking_app.event_service.entity.EventCategory;
import com.event_booking_app.event_service.entity.EventStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventResponse {
    private UUID id;
    private String title;
    private String description;
    private EventCategory category;
    private EventStatus status;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private UUID organizerId;
    private VenueResponse venue;
    private Instant createdAt;
    private Instant updatedAt;
}
