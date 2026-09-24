package com.event_booking_app.inventory_service.repository;

import com.event_booking_app.inventory_service.entity.HoldStatus;
import com.event_booking_app.inventory_service.entity.SeatHold;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeatHoldRepository extends JpaRepository<SeatHold, UUID> {
    Optional<SeatHold> findByIdempotencyKey(UUID idempotencyKey);

    List<SeatHold> findByStatusAndExpiresAtBefore(HoldStatus holdStatus, Instant now);
}
