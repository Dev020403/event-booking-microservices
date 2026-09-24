package com.event_booking_app.inventory_service.service;

import com.event_booking_app.inventory_service.dto.HoldRequest;
import com.event_booking_app.inventory_service.dto.HoldResponse;
import com.event_booking_app.inventory_service.entity.HoldStatus;
import com.event_booking_app.inventory_service.entity.Inventory;
import com.event_booking_app.inventory_service.entity.SeatHold;
import com.event_booking_app.inventory_service.exception.HoldNotFoundException;
import com.event_booking_app.inventory_service.exception.InsufficientInventoryException;
import com.event_booking_app.inventory_service.exception.InvalidHoldStateException;
import com.event_booking_app.inventory_service.exception.InventoryNotFoundException;
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
@Slf4j
@RequiredArgsConstructor
public class HoldServiceImpl implements HoldService {

    private final SeatHoldRepository seatHoldRepository;
    private final InventoryRepository inventoryRepository;
    private final HoldMapper holdMapper;

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
        var existing = seatHoldRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existing.isPresent()) {
            log.info("Idempotent hold request - returning existing hold {} for key {}",
                    existing.get().getId(), request.getIdempotencyKey());
            return holdMapper.toResponse(existing.get());
        }

        Inventory inventory = inventoryRepository.findById(inventoryId)
                .orElseThrow(() -> new InventoryNotFoundException(inventoryId));

        if (inventory.getAvailableQuantity() < request.getQuantity()) {
            throw new InsufficientInventoryException(inventoryId, request.getQuantity(), inventory.getAvailableQuantity());
        }

        // Update inventory as we are holding inventory
        inventory.setAvailableQuantity(inventory.getAvailableQuantity() - request.getQuantity());
        inventory.setHeldQuantity(inventory.getHeldQuantity() + request.getQuantity());
        inventoryRepository.save(inventory);

        // Create the hold record
        SeatHold hold = holdMapper.toEntity(inventoryId, request, defaultTtlMinutes);
        SeatHold savedHold = seatHoldRepository.save(hold);

        log.info("Held {} units of inventory {} for user {} (holdId={}, expiresAt={})",
                request.getQuantity(), inventoryId, request.getUserId(),
                savedHold.getId(), savedHold.getExpiresAt());

        return holdMapper.toResponse(savedHold);
    }

    @Recover
    public HoldResponse recoverHoldFromOptimisticLockFailure(
            ObjectOptimisticLockingFailureException ex, UUID inventoryId, HoldRequest request) {
        log.error("Exhausted retries holding inventory {} for user {} — high contention on this row",
                inventoryId, request.getUserId(), ex);
        throw new InsufficientInventoryException(inventoryId, request.getQuantity(), -1);
    }

    @Override
    @Transactional
    @Retryable(retryFor = ObjectOptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 100, multiplier = 2))
    public HoldResponse confirmHold(UUID holdId) {
        SeatHold hold = seatHoldRepository.findById(holdId)
                .orElseThrow(() -> new HoldNotFoundException(holdId));

        // Guard: only a HELD hold can be confirmed
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

        releaseHoldInternal(hold, HoldStatus.RELEASED);
        return holdMapper.toResponse(hold);
    }

    @Override
    @Transactional(readOnly = true)
    public HoldResponse getHold(UUID holdId) {
        SeatHold hold = seatHoldRepository.findById(holdId)
                .orElseThrow(() -> new HoldNotFoundException(holdId));
        return holdMapper.toResponse(hold);
    }

    private void releaseHoldInternal(SeatHold hold, HoldStatus newStatus) {
        Inventory inventory = inventoryRepository.findById(hold.getInventoryId())
                .orElseThrow(() -> new InventoryNotFoundException(hold.getInventoryId()));

        inventory.setHeldQuantity(inventory.getHeldQuantity() - hold.getQuantity());
        inventory.setAvailableQuantity(inventory.getAvailableQuantity() + hold.getQuantity());
        inventoryRepository.save(inventory);

        hold.setStatus(newStatus);
        seatHoldRepository.save(hold);

        log.info("{} hold {} — {} units returned to available on inventory {}",
                newStatus, hold.getId(), hold.getQuantity(), hold.getInventoryId());
    }
}