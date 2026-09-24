package com.event_booking_app.inventory_service.service;

import com.event_booking_app.inventory_service.dto.HoldRequest;
import com.event_booking_app.inventory_service.dto.HoldResponse;
import com.event_booking_app.inventory_service.entity.HoldStatus;
import com.event_booking_app.inventory_service.entity.Inventory;
import com.event_booking_app.inventory_service.entity.SeatHold;
import com.event_booking_app.inventory_service.exception.*;
import com.event_booking_app.inventory_service.kafka.InventoryEventPublisher;
import com.event_booking_app.inventory_service.kafka.event.InventoryConfirmedEvent;
import com.event_booking_app.inventory_service.kafka.event.InventoryHeldEvent;
import com.event_booking_app.inventory_service.kafka.event.InventoryReleasedEvent;
import com.event_booking_app.inventory_service.mapper.HoldMapper;
import com.event_booking_app.inventory_service.repository.InventoryRepository;
import com.event_booking_app.inventory_service.repository.SeatHoldRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class HoldServiceImpl implements HoldService {

    private final InventoryRepository inventoryRepository;
    private final SeatHoldRepository seatHoldRepository;
    private final HoldMapper holdMapper;
    private final InventoryEventPublisher inventoryEventPublisher;

    @Value("${holds.default-ttl-minutes:10}")
    private int defaultTtlMinutes;

    @Override
    @Transactional
    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public HoldResponse holdInventory(UUID inventoryId, HoldRequest request) {

        // Idempotency check first: if a hold with this key already exists,
        // this is a retried request (e.g. Booking Service timed out and
        // retried) — return the existing hold rather than double-holding.
        var existing = seatHoldRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existing.isPresent()) {
            log.info("Idempotent hold request — returning existing hold {} for key {}",
                    existing.get().getId(), request.getIdempotencyKey());
            return holdMapper.toResponse(existing.get());
        }

        Inventory inventory = inventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new InventoryNotFoundException(inventoryId));

        if (inventory.getAvailableQuantity() < request.getQuantity()) {
            throw new InsufficientInventoryException(
                    inventoryId, request.getQuantity(), inventory.getAvailableQuantity());
        }

        // Mutate and save — this is the operation that can throw
        // ObjectOptimisticLockingFailureException if another transaction
        // updated this same row (and bumped its @Version) between our read
        // above and this save. @Retryable catches that, and on retry we
        // re-enter this whole method, so inventoryRepository.findById runs
        // again and gets the fresh version — not a stale one.
        inventory.setAvailableQuantity(inventory.getAvailableQuantity() - request.getQuantity());
        inventory.setHeldQuantity(inventory.getHeldQuantity() + request.getQuantity());
        inventoryRepository.save(inventory);

        SeatHold hold = holdMapper.toEntity(inventoryId, request, defaultTtlMinutes);
        SeatHold savedHold = seatHoldRepository.save(hold);

        log.info("Held {} units of inventory {} for user {} (holdId={}, expiresAt={})",
                request.getQuantity(), inventoryId, request.getUserId(),
                savedHold.getId(), savedHold.getExpiresAt());

        inventoryEventPublisher.publish(
                inventoryId.toString(),
                new InventoryHeldEvent(
                        inventoryId, inventory.getEventId(), inventory.getTicketTypeId(),
                        savedHold.getId(), request.getQuantity())
        );

        return holdMapper.toResponse(savedHold);
    }

    // Called automatically if all @Retryable attempts are exhausted.
    // Surfaces a clear error instead of letting the last
    // ObjectOptimisticLockingFailureException propagate raw.
    @Recover
    public HoldResponse recoverFromOptimisticLockFailure(
            ObjectOptimisticLockingFailureException ex, UUID inventoryId, HoldRequest request) {
        log.error("Exhausted retries holding inventory {} for user {} — high contention on this row",
                inventoryId, request.getUserId(), ex);
        throw new InsufficientInventoryException(
                inventoryId, request.getQuantity(), -1); // -1: availability unknown after exhausted retries
    }

    @Override
    @Transactional
    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 100, multiplier = 2))
    public HoldResponse confirmHold(UUID holdId) {
        SeatHold hold = seatHoldRepository.findById(holdId)
                .orElseThrow(() -> new HoldNotFoundException(holdId));

        if (hold.getStatus() != HoldStatus.HELD) {
            throw new InvalidHoldStateException(holdId, hold.getStatus(), "confirm");
        }

        Inventory inventory = inventoryRepository.findById(hold.getInventoryId())
                .orElseThrow(() -> new InventoryNotFoundException(hold.getInventoryId()));

        inventory.setHeldQuantity(inventory.getHeldQuantity() - hold.getQuantity());
        inventory.setBookedQuantity(inventory.getBookedQuantity() + hold.getQuantity());
        inventoryRepository.save(inventory);

        hold.setStatus(HoldStatus.CONFIRMED);
        SeatHold saved = seatHoldRepository.save(hold);

        log.info("Confirmed hold {} — {} units now booked on inventory {}",
                holdId, hold.getQuantity(), hold.getInventoryId());

        inventoryEventPublisher.publish(
                hold.getInventoryId().toString(),
                new InventoryConfirmedEvent(hold.getInventoryId(), holdId, hold.getQuantity())
        );

        return holdMapper.toResponse(saved);
    }

    @Recover
    public HoldResponse recoverConfirmFromOptimisticLockFailure(
            ObjectOptimisticLockingFailureException ex, UUID holdId) {
        log.error("Exhausted retries confirming hold {} — high contention on inventory row", holdId, ex);
        throw new InvalidHoldStateException("Failed to confirm hold " + holdId + " due to high contention. Please retry.");
    }

    @Override
    @Transactional
    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 100, multiplier = 2))
    public HoldResponse releaseHold(UUID holdId) {
        SeatHold hold = seatHoldRepository.findById(holdId)
                .orElseThrow(() -> new HoldNotFoundException(holdId));

        if (hold.getStatus() != HoldStatus.HELD) {
            throw new InvalidHoldStateException(holdId, hold.getStatus(), "release");
        }

        releaseHoldInternal(hold, HoldStatus.RELEASED, "manual release");
        return holdMapper.toResponse(hold);
    }

    @Recover
    public HoldResponse recoverReleaseFromOptimisticLockFailure(
            ObjectOptimisticLockingFailureException ex, UUID holdId) {
        log.error("Exhausted retries releasing hold {} — high contention on inventory row", holdId, ex);
        throw new InvalidHoldStateException("Failed to release hold " + holdId + " due to high contention. Please retry.");
    }

    /**
     * Shared logic between explicit release and expiry — both convert
     * held -> available on Inventory and flip the SeatHold's status.
     * Package-private so HoldExpiryScheduler can reuse it, passing
     * "expired" as the reason instead of "manual release".
     */
    void releaseHoldInternal(SeatHold hold, HoldStatus newStatus, String reason) {
        Inventory inventory = inventoryRepository.findById(hold.getInventoryId())
                .orElseThrow(() -> new InventoryNotFoundException(hold.getInventoryId()));

        inventory.setHeldQuantity(inventory.getHeldQuantity() - hold.getQuantity());
        inventory.setAvailableQuantity(inventory.getAvailableQuantity() + hold.getQuantity());
        inventoryRepository.save(inventory);

        hold.setStatus(newStatus);
        seatHoldRepository.save(hold);

        log.info("{} hold {} — {} units returned to available on inventory {} ({})",
                newStatus, hold.getId(), hold.getQuantity(), hold.getInventoryId(), reason);

        inventoryEventPublisher.publish(
                hold.getInventoryId().toString(),
                new InventoryReleasedEvent(hold.getInventoryId(), hold.getId(), hold.getQuantity(), reason)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public HoldResponse getHold(UUID holdId) {
        SeatHold hold = seatHoldRepository.findById(holdId)
                .orElseThrow(() -> new HoldNotFoundException(holdId));
        return holdMapper.toResponse(hold);
    }

    @Override
    @Transactional
    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 100, multiplier = 2))
    public void expireHold(UUID holdId) {
        SeatHold hold = seatHoldRepository.findById(holdId)
                .orElseThrow(() -> new HoldNotFoundException(holdId));

        // Not an error — a concurrent confirm/release could have already
        // resolved this hold between the scheduler's query and this call.
        if (hold.getStatus() != HoldStatus.HELD) {
            log.info("Skipping expiry for hold {} — already {}", holdId, hold.getStatus());
            return;
        }

        releaseHoldInternal(hold, HoldStatus.EXPIRED, "expired");
    }

    @Recover
    public void recoverExpireFromOptimisticLockFailure(ObjectOptimisticLockingFailureException ex, UUID holdId) {
        // Don't throw here — this runs inside the scheduler's loop, and one
        // hold failing to expire (even after retries) shouldn't be treated
        // as fatal. It'll simply be picked up again on the next scheduled run.
        log.error("Exhausted retries expiring hold {} — will retry on next scheduled run", holdId, ex);
    }
}