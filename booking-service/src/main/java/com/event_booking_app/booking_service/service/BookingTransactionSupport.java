package com.event_booking_app.booking_service.service;

import com.event_booking_app.booking_service.client.InventoryClient;
import com.event_booking_app.booking_service.entity.Booking;
import com.event_booking_app.booking_service.entity.BookingStatus;
import com.event_booking_app.booking_service.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * A separate Spring bean that owns all short, self-contained transactions for the
 * booking service. Keeping these methods here (rather than inside BookingServiceImpl)
 * means Spring's proxy is used for every call, so @Transactional annotations are
 * respected — no more self-call proxy bypass.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingTransactionSupport {

    private final BookingRepository bookingRepository;
    private final InventoryClient inventoryClient;

    /**
     * Persists a brand-new booking and flushes immediately so that any constraint
     * violation surfaces here (inside the try/catch in the caller) rather than at
     * commit time — fixing Bug 3 (orphaned hold).
     */
    @Transactional
    public Booking saveNewBooking(Booking booking) {
        return bookingRepository.saveAndFlush(booking);
    }

    /**
     * Records a payment failure durably. Because this lives in a separate bean,
     * Spring's proxy is used for every call site, so the method always runs in its
     * own independent transaction — fixing Bug 2 (PAYMENT_FAILED rolled back when
     * the outer transaction throws).
     *
     * Note: when called from createBooking there IS no outer transaction, so a
     * plain @Transactional is enough.  When called from confirmBookingPayment
     * (which IS @Transactional), Spring will suspend the outer transaction and
     * start a new one, committing it before the outer transaction rolls back.
     *
     * @param bookingId ID of the booking to mark as PAYMENT_FAILED
     * @param holdId    hold to release as a compensating action (may be null)
     * @param reason    human-readable failure reason (for logging)
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void persistPaymentFailure(UUID bookingId, UUID holdId, String reason) {
        log.warn("Persisting PAYMENT_FAILED for bookingId={}, reason={}", bookingId, reason);

        // Compensating action: release the inventory hold
        if (holdId != null) {
            try {
                inventoryClient.releaseHold(holdId);
            } catch (Exception e) {
                log.error("Failed to release hold {} during payment failure: {}", holdId, e.getMessage());
            }
        }

        // Fresh load — the booking was committed before we got here, so findById is safe
        bookingRepository.findById(bookingId).ifPresentOrElse(
                b -> {
                    b.setStatus(BookingStatus.PAYMENT_FAILED);
                    bookingRepository.saveAndFlush(b);
                    log.info("Booking {} status set to PAYMENT_FAILED", bookingId);
                },
                () -> log.error("Could not find booking {} to record PAYMENT_FAILED — this is a data-integrity gap", bookingId)
        );
    }
}
