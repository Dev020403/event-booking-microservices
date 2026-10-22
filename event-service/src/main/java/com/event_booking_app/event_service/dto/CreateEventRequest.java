package com.event_booking_app.event_service.dto;

import com.event_booking_app.event_service.entity.EventCategory;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.validation.constraints.Size;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateEventRequest {

    @NotBlank(message = "Title is required")
    @Size(min = 3, max = 255, message = "Title must be between 3 and 255 characters")
    private String title;

    @NotBlank(message = "Description is required")
    @Size(min = 10, max = 5000, message = "Description must be between 10 and 5000 characters")
    private String description;

    @NotNull(message = "Category is required")
    private EventCategory category;

    @NotNull(message = "Start date/time is required")
    @Future(message = "Start date/time must be in the future")
    private LocalDateTime startDateTime;

    @NotNull(message = "End date/time is required")
    @Future(message = "End date/time must be in the future")
    private LocalDateTime endDateTime;

    @NotNull(message = "Venue ID is required")
    private UUID venueId;
}
