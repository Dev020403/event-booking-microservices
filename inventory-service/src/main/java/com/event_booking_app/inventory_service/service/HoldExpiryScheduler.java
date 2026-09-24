package com.event_booking_app.inventory_service.service;

import com.event_booking_app.inventory_service.entity.HoldStatus;
import com.event_booking_app.inventory_service.entity.SeatHold;
import com.event_booking_app.inventory_service.repository.SeatHoldRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Polls for holds past their TTL and releases them back to available
 * stock. Chosen over Redis TTL + expiry pub/sub for simplicity — see
 * earlier design discussion. The tradeoff: an expired hold can linger up
 * to fixedRate past its actual expiresAt before this job catches it.
 * With a ~10 minute hold window, a default 60s poll interval is
 * inconsequential slack in practice.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HoldExpiryScheduler {

    private final SeatHoldRepository seatHoldRepository;
    private final HoldService holdService;

    @Scheduled(fixedRateString = "${holds.expiry-poll-interval-ms:60000}")
    public void releaseExpiredHolds() {
        List<SeatHold> expired = seatHoldRepository.findByStatusAndExpiresAtBefore(
                HoldStatus.HELD, Instant.now());

        if (expired.isEmpty()) {
            return;
        }

        log.info("Found {} expired hold(s) to release", expired.size());

        for (SeatHold hold : expired) {
            try {
                holdService.expireHold(hold.getId());
            } catch (Exception ex) {
                // One bad hold shouldn't stop the rest of the batch from
                // being processed — log and move on, it'll be retried
                // (still HELD and still past expiresAt) on the next run.
                log.error("Failed to expire hold {} — will retry on next scheduled run", hold.getId(), ex);
            }
        }
    }
}