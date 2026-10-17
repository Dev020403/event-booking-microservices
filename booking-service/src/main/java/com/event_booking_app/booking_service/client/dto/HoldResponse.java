package com.event_booking_app.booking_service.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoldResponse {
    private UUID holdId;
    private UUID inventoryId;
    private UUID userId;
    private int quantity;
    private String status;
    private Instant heldAt;
    private Instant expiresAt;
}
