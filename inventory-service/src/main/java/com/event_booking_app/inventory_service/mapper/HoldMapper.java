package com.event_booking_app.inventory_service.mapper;

import com.event_booking_app.inventory_service.dto.HoldRequest;
import com.event_booking_app.inventory_service.dto.HoldResponse;
import com.event_booking_app.inventory_service.entity.HoldStatus;
import com.event_booking_app.inventory_service.entity.SeatHold;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Component
public class HoldMapper {

    public HoldResponse toResponse(SeatHold hold) {
        return HoldResponse.builder()
                .holdId(hold.getId())
                .inventoryId(hold.getInventoryId())
                .userId(hold.getUserId())
                .quantity(hold.getQuantity())
                .status(hold.getStatus())
                .heldAt(hold.getHeldAt())
                .expiresAt(hold.getExpiresAt())
                .build();
    }

    public SeatHold toEntity(UUID inventoryId, HoldRequest request, int ttlMinutes) {
        return SeatHold.builder()
                .inventoryId(inventoryId)
                .userId(request.getUserId())
                .quantity(request.getQuantity())
                .status(HoldStatus.HELD)
                .idempotencyKey(request.getIdempotencyKey())
                .expiresAt(Instant.now().plus(ttlMinutes, ChronoUnit.MINUTES))
                .build();
    }
}