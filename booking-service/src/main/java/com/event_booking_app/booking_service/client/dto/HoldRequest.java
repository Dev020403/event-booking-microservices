package com.event_booking_app.booking_service.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HoldRequest {
    private UUID userId;
    private Integer quantity;
    private UUID idempotencyKey;
}
