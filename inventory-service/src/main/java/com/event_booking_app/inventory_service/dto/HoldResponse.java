package com.event_booking_app.inventory_service.dto;

import com.event_booking_app.inventory_service.entity.HoldStatus;
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
    private HoldStatus status;
    private Instant heldAt;
    private Instant expiresAt;
}