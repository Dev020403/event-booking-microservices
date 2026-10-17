package com.event_booking_app.booking_service.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private String status;
    private String category;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;
    private String organizerId;
}
