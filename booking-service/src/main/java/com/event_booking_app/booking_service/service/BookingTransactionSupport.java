package com.event_booking_app.booking_service.service;

import com.event_booking_app.booking_service.entity.Booking;
import com.event_booking_app.booking_service.entity.BookingStatus;
import com.event_booking_app.booking_service.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * A separate Spring bean that owns all short, self-contained transactions for the
 * booking service. Keeping these methods here (rather than inside BookingServiceImpl)
 * means Spring's proxy is used for every call, so @Transactional annotations are
 * respected — no more self-call proxy bypass.
 *
 * Remote calls (payment, inventory) are made by the caller between these
 * transactions, so no DB connection or row lock is held while waiting on them.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingTransactionSupport {

    private final BookingRepository bookingRepository;

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
     * Atomically moves a booking from {@code from} to {@code to}.
     *
     * @return true if this caller won the transition, false if the booking was no
     *         longer in {@code from} (another request changed it first)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean transitionStatus(UUID bookingId, BookingStatus from, BookingStatus to) {
        return bookingRepository.transitionStatus(bookingId, from, to, Instant.now()) == 1;
    }

    /**
     * PENDING -> CONFIRMED, recording the payment. Only the request that claimed the
     * booking for payment (HELD -> PENDING) can reach this.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markConfirmed(UUID bookingId, UUID paymentId) {
        return bookingRepository.transitionStatusWithPayment(
                bookingId, BookingStatus.PENDING, BookingStatus.CONFIRMED, paymentId, Instant.now()) == 1;
    }

    /**
     * Records a payment failure durably (PENDING -> PAYMENT_FAILED) in its own
     * transaction. The caller is responsible for releasing the inventory hold.
     *
     * @param bookingId ID of the booking to mark as PAYMENT_FAILED
     * @param reason    human-readable failure reason (for logging)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean persistPaymentFailure(UUID bookingId, String reason) {
        log.warn("Persisting PAYMENT_FAILED for bookingId={}, reason={}", bookingId, reason);

        int updated = bookingRepository.transitionStatus(
                bookingId, BookingStatus.PENDING, BookingStatus.PAYMENT_FAILED, Instant.now());
        if (updated == 1) {
            log.info("Booking {} status set to PAYMENT_FAILED", bookingId);
            return true;
        }
        log.error("Could not mark booking {} PAYMENT_FAILED — it was not PENDING", bookingId);
        return false;
    }
}
