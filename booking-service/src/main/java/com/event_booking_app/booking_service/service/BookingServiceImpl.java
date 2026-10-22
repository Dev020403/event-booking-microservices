package com.event_booking_app.booking_service.service;

import com.event_booking_app.booking_service.client.EventClient;
import com.event_booking_app.booking_service.client.InventoryClient;
import com.event_booking_app.booking_service.client.PaymentClient;
import com.event_booking_app.booking_service.client.dto.*;
import com.event_booking_app.booking_service.dto.BookingResponse;
import com.event_booking_app.booking_service.dto.ConfirmPaymentRequest;
import com.event_booking_app.booking_service.dto.CreateBookingRequest;
import com.event_booking_app.booking_service.entity.Booking;
import com.event_booking_app.booking_service.entity.BookingStatus;
import com.event_booking_app.booking_service.exception.BookingAlreadyExistsException;
import com.event_booking_app.booking_service.exception.BookingNotFoundException;
import com.event_booking_app.booking_service.exception.BookingPaymentFailedException;
import com.event_booking_app.booking_service.exception.InvalidBookingStateException;
import com.event_booking_app.booking_service.kafka.BookingEventPublisher;
import com.event_booking_app.booking_service.kafka.event.BookingCancelledEvent;
import com.event_booking_app.booking_service.kafka.event.BookingConfirmedEvent;
import com.event_booking_app.booking_service.kafka.event.BookingCreatedEvent;
import com.event_booking_app.booking_service.mapper.BookingMapper;
import com.event_booking_app.booking_service.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingServiceImpl implements BookingService {

    private static final String EVENT_STATUS_PUBLISHED = "PUBLISHED";
    private static final String HOLD_STATUS_HELD = "HELD";

    private final BookingRepository bookingRepository;
    private final BookingMapper bookingMapper;
    private final EventClient eventClient;
    private final InventoryClient inventoryClient;
    private final PaymentClient paymentClient;
    private final BookingEventPublisher eventPublisher;

    /**
     * Separate bean — NOT self-injected — so Spring's proxy is always used and
     * @Transactional on its methods is always honoured.
     */
    private final BookingTransactionSupport txSupport;

    @Value("${payment.default-currency:INR}")
    private String defaultCurrency;

    // ── Transaction strategy ──────────────────────────────────────────────────
    //
    // The write paths (create / confirm / cancel) are intentionally NOT
    // @Transactional. They orchestrate remote calls (event, inventory, payment),
    // and holding a DB transaction open across those calls would pin a
    // connection and make status updates invisible to concurrent requests.
    //
    // Instead, every status change is an atomic compare-and-set in its own short
    // transaction via txSupport (UPDATE ... WHERE status = :from). Exactly one
    // request can move a booking HELD -> PENDING, so two concurrent confirm
    // calls can no longer both charge the customer.
    // ─────────────────────────────────────────────────────────────────────────
    @Override
    public BookingResponse createBooking(CreateBookingRequest request, String currentUserId) {
        log.info("Creating booking for userId={}, eventId={}, ticketTypeId={}, quantity={}",
                currentUserId, request.getEventId(), request.getTicketTypeId(), request.getQuantity());

        UUID userId = UUID.fromString(currentUserId);

        // 1. Idempotency check
        Optional<Booking> existing = bookingRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existing.isPresent()) {
            log.info("Found existing booking with idempotency key: {}", request.getIdempotencyKey());
            return bookingMapper.toResponse(requireSameOwner(existing.get(), userId, request.getIdempotencyKey()));
        }

        // 2. Validate ticket type (existence + event ownership + sale window)
        TicketTypeResponse ticketType = eventClient.getTicketTypeById(request.getTicketTypeId());
        if (ticketType == null) {
            throw new InvalidBookingStateException("Invalid ticket type ID: " + request.getTicketTypeId());
        }
        if (!request.getEventId().equals(ticketType.getEventId())) {
            throw new InvalidBookingStateException("Ticket type " + request.getTicketTypeId()
                    + " does not belong to event " + request.getEventId());
        }
        if (ticketType.getPrice() == null) {
            throw new InvalidBookingStateException("Ticket type " + request.getTicketTypeId() + " has no price");
        }
        validateSalesWindow(ticketType);

        // 3. Only published events can be booked
        EventResponse event = eventClient.getEventById(request.getEventId());
        if (event == null || !EVENT_STATUS_PUBLISHED.equals(event.getStatus())) {
            throw new InvalidBookingStateException("Event " + request.getEventId() + " is not open for booking");
        }

        // 4. Fetch inventory record
        InventoryResponse inventory = inventoryClient.getInventory(request.getEventId(), request.getTicketTypeId());
        if (inventory == null) {
            throw new InvalidBookingStateException("No inventory found for eventId="
                    + request.getEventId() + " ticketTypeId=" + request.getTicketTypeId());
        }

        // 5. Hold seats in Inventory Service (idempotent on the same key)
        HoldRequest holdRequest = HoldRequest.builder()
                .userId(userId)
                .quantity(request.getQuantity())
                .idempotencyKey(request.getIdempotencyKey())
                .build();
        HoldResponse holdResponse;
        try {
            holdResponse = inventoryClient.holdInventory(inventory.getId(), holdRequest);
        } catch (Exception ex) {
            log.error("Failed to hold inventory: {}", ex.getMessage());
            throw new InvalidBookingStateException("Failed to hold inventory for ticket: " + ex.getMessage());
        }

        // Derive currency from ticket type; fall back to configurable default
        BigDecimal unitPrice = ticketType.getPrice();
        BigDecimal totalAmount = unitPrice.multiply(BigDecimal.valueOf(request.getQuantity()));
        String currency = (ticketType.getCurrency() != null && !ticketType.getCurrency().isBlank())
                ? ticketType.getCurrency()
                : defaultCurrency;

        // 6. Persist the HELD booking — committed immediately via saveAndFlush inside txSupport.
        Booking booking = Booking.builder()
                .userId(userId)
                .eventId(request.getEventId())
                .ticketTypeId(request.getTicketTypeId())
                .inventoryId(inventory.getId())
                .holdId(holdResponse.getHoldId())
                .quantity(request.getQuantity())
                .unitPrice(unitPrice)
                .totalAmount(totalAmount)
                .currency(currency)
                .status(BookingStatus.HELD)
                .idempotencyKey(request.getIdempotencyKey())
                .build();

        Booking savedBooking;
        try {
            savedBooking = txSupport.saveNewBooking(booking);
        } catch (DataIntegrityViolationException ex) {
            // A concurrent request with the same idempotency key won the insert. Inventory
            // returned that request's hold to us too, so releasing it here would strip the
            // seats from the booking that was actually saved.
            Optional<Booking> winner = bookingRepository.findByIdempotencyKey(request.getIdempotencyKey());
            if (winner.isPresent()) {
                log.info("Concurrent create for idempotency key {} — returning booking {}",
                        request.getIdempotencyKey(), winner.get().getId());
                return bookingMapper.toResponse(requireSameOwner(winner.get(), userId, request.getIdempotencyKey()));
            }
            releaseHoldQuietly(holdResponse.getHoldId(), "booking-save rollback");
            throw new InvalidBookingStateException("Failed to create booking: " + ex.getMostSpecificCause().getMessage());
        } catch (Exception ex) {
            log.error("Failed to persist booking after holding inventory {}. Releasing hold.", holdResponse.getHoldId());
            releaseHoldQuietly(holdResponse.getHoldId(), "booking-save rollback");
            throw new InvalidBookingStateException("Failed to create booking: " + ex.getMessage());
        }
        // ── The HELD booking is now committed to the DB. ──

        // 7. Process payment if a payment method was supplied inline
        if (request.getPaymentMethod() != null) {
            Booking finalBooking = processPaymentAndFinalizeBooking(savedBooking, request.getPaymentMethod(),
                    request.getIdempotencyKey(), currency);
            return bookingMapper.toResponse(finalBooking);
        }

        // Publish booking-created event for downstream services (e.g. notification)
        eventPublisher.publishBookingCreated(BookingCreatedEvent.builder()
                .bookingId(savedBooking.getId())
                .userId(savedBooking.getUserId())
                .eventId(savedBooking.getEventId())
                .ticketTypeId(savedBooking.getTicketTypeId())
                .quantity(savedBooking.getQuantity())
                .totalAmount(savedBooking.getTotalAmount())
                .status(savedBooking.getStatus().name())
                .createdAt(savedBooking.getCreatedAt())
                .build());

        return bookingMapper.toResponse(savedBooking);
    }

    @Override
    public BookingResponse confirmBookingPayment(UUID bookingId, ConfirmPaymentRequest request, String currentUserId) {
        log.info("Confirming payment for bookingId: {}", bookingId);

        Booking booking = findBooking(bookingId);
        validateBookingOwnership(booking, currentUserId, false);

        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            return bookingMapper.toResponse(booking);
        }

        // Derive currency stored on booking, fall back to configured default
        String currency = (booking.getCurrency() != null && !booking.getCurrency().isBlank())
                ? booking.getCurrency()
                : defaultCurrency;
        Booking finalBooking = processPaymentAndFinalizeBooking(
                booking, request.getPaymentMethod(), request.getIdempotencyKey(), currency);

        return bookingMapper.toResponse(finalBooking);
    }

    @Override
    public BookingResponse cancelBooking(UUID bookingId, String currentUserId, boolean isAdmin) {
        log.info("Cancelling bookingId: {} requested by userId: {}, isAdmin: {}", bookingId, currentUserId, isAdmin);

        Booking booking = findBooking(bookingId);
        validateBookingOwnership(booking, currentUserId, isAdmin);

        switch (booking.getStatus()) {
            case CANCELLED:
                return bookingMapper.toResponse(booking);

            case HELD:
                if (!txSupport.transitionStatus(bookingId, BookingStatus.HELD, BookingStatus.CANCELLED)) {
                    throw concurrentChange(bookingId);
                }
                if (booking.getHoldId() != null) {
                    releaseHoldQuietly(booking.getHoldId(), "cancellation");
                }
                break;

            case CONFIRMED:
                // Refund first: if it fails, the booking stays CONFIRMED and the user can retry.
                // Refunds are idempotent in Payment Service, so a retry after a partial failure is safe.
                if (booking.getPaymentId() != null) {
                    try {
                        paymentClient.refundPayment(booking.getPaymentId(), RefundRequest.builder()
                                .reason("Booking cancelled by " + (isAdmin ? "admin" : "user"))
                                .build());
                    } catch (Exception ex) {
                        log.error("Failed to refund payment for booking {}: {}", booking.getId(), ex.getMessage());
                        throw new InvalidBookingStateException("Failed to refund payment: " + ex.getMessage());
                    }
                }
                if (!txSupport.transitionStatus(bookingId, BookingStatus.CONFIRMED, BookingStatus.CANCELLED)) {
                    throw concurrentChange(bookingId);
                }
                break;

            case PENDING:
                throw new InvalidBookingStateException("Payment for booking " + bookingId
                        + " is in progress and it cannot be cancelled right now");

            default:
                throw new InvalidBookingStateException("Cannot cancel booking with status: " + booking.getStatus());
        }

        Booking saved = findBooking(bookingId);

        eventPublisher.publishBookingCancelled(BookingCancelledEvent.builder()
                .bookingId(saved.getId())
                .userId(saved.getUserId())
                .eventId(saved.getEventId())
                .ticketTypeId(saved.getTicketTypeId())
                .quantity(saved.getQuantity())
                .cancelledAt(Instant.now())
                .build());

        log.info("Booking {} successfully CANCELLED", bookingId);
        return bookingMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse getBookingById(UUID bookingId, String currentUserId, boolean isAdmin) {
        Booking booking = findBooking(bookingId);
        validateBookingOwnership(booking, currentUserId, isAdmin);
        return bookingMapper.toResponse(booking);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BookingResponse> getUserBookings(String currentUserId, BookingStatus status, Pageable pageable) {
        UUID userId = UUID.fromString(currentUserId);
        Page<Booking> page = (status != null)
                ? bookingRepository.findByUserIdAndStatus(userId, status, pageable)
                : bookingRepository.findByUserId(userId, pageable);
        return page.map(bookingMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BookingResponse> getAllBookings(BookingStatus status, Pageable pageable) {
        Page<Booking> page = (status != null)
                ? bookingRepository.findByStatus(status, pageable)
                : bookingRepository.findAll(pageable);
        return page.map(bookingMapper::toResponse);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Claims the booking for payment (HELD -> PENDING), charges it, confirms the
     * inventory hold and marks it CONFIRMED. Returns the booking's final state.
     *
     * Compensation:
     *  - payment fails            -> release hold, PAYMENT_FAILED
     *  - hold confirmation fails  -> refund payment, release hold, PAYMENT_FAILED
     */
    private Booking processPaymentAndFinalizeBooking(Booking booking, PaymentMethod paymentMethod,
                                                     UUID idempotencyKey, String currency) {
        UUID bookingId = booking.getId();

        // Only one request can win this transition, so only one can charge the customer.
        if (!txSupport.transitionStatus(bookingId, BookingStatus.HELD, BookingStatus.PENDING)) {
            Booking current = findBooking(bookingId);
            if (current.getStatus() == BookingStatus.CONFIRMED) {
                return current;
            }
            if (current.getStatus() == BookingStatus.PENDING) {
                throw new InvalidBookingStateException("Payment for booking " + bookingId + " is already in progress");
            }
            throw new InvalidBookingStateException("Booking cannot be paid. Current status: " + current.getStatus());
        }

        // Don't charge for seats we no longer hold (the hold TTL may have lapsed).
        if (booking.getHoldId() != null) {
            String holdProblem = checkHoldStillActive(booking.getHoldId());
            if (holdProblem != null) {
                failPayment(bookingId, booking.getHoldId(), holdProblem);
                throw new InvalidBookingStateException("Booking can no longer be paid: " + holdProblem);
            }
        }

        ProcessPaymentRequest paymentRequest = ProcessPaymentRequest.builder()
                .bookingId(bookingId)
                .amount(booking.getTotalAmount())
                .currency(currency)
                .paymentMethod(paymentMethod)
                .idempotencyKey(idempotencyKey != null ? idempotencyKey : UUID.randomUUID())
                .build();

        PaymentResponse paymentResponse;
        try {
            paymentResponse = paymentClient.processPayment(paymentRequest);
        } catch (Exception ex) {
            log.error("Payment processing failed for booking {}: {}", bookingId, ex.getMessage());
            failPayment(bookingId, booking.getHoldId(), ex.getMessage());
            throw new BookingPaymentFailedException("Payment processing failed: " + ex.getMessage());
        }

        if (paymentResponse == null || paymentResponse.getStatus() != PaymentStatus.SUCCESS) {
            failPayment(bookingId, booking.getHoldId(), "Payment returned non-SUCCESS status: "
                    + (paymentResponse != null ? paymentResponse.getStatus() : "null response"));
            throw new BookingPaymentFailedException("Payment processing returned unsuccessful status");
        }

        // Payment succeeded — convert the hold into booked seats.
        if (booking.getHoldId() != null) {
            try {
                inventoryClient.confirmHold(booking.getHoldId());
            } catch (Exception e) {
                log.error("Failed to confirm inventory hold {}: {}", booking.getHoldId(), e.getMessage());
                refundQuietly(paymentResponse.getId(), bookingId);
                failPayment(bookingId, booking.getHoldId(), "Failed to confirm inventory hold: " + e.getMessage());
                throw new InvalidBookingStateException("Hold confirmation failed: " + e.getMessage());
            }
        }

        if (!txSupport.markConfirmed(bookingId, paymentResponse.getId())) {
            // Shouldn't happen: this request owns the PENDING state. Surface it loudly.
            log.error("Booking {} left PENDING unexpectedly; payment {} succeeded and hold {} is confirmed",
                    bookingId, paymentResponse.getId(), booking.getHoldId());
            throw concurrentChange(bookingId);
        }

        Booking confirmed = findBooking(bookingId);
        eventPublisher.publishBookingConfirmed(BookingConfirmedEvent.builder()
                .bookingId(confirmed.getId())
                .userId(confirmed.getUserId())
                .eventId(confirmed.getEventId())
                .ticketTypeId(confirmed.getTicketTypeId())
                .quantity(confirmed.getQuantity())
                .totalAmount(confirmed.getTotalAmount())
                .paymentId(paymentResponse.getId())
                .confirmedAt(Instant.now())
                .build());
        log.info("Booking {} successfully CONFIRMED with payment {}", bookingId, paymentResponse.getId());
        return confirmed;
    }

    /**
     * @return null if the hold is still HELD and unexpired, otherwise a reason
     */
    private String checkHoldStillActive(UUID holdId) {
        HoldResponse hold;
        try {
            hold = inventoryClient.getHold(holdId);
        } catch (Exception ex) {
            return "could not verify inventory hold " + holdId + ": " + ex.getMessage();
        }
        if (hold == null || !HOLD_STATUS_HELD.equals(hold.getStatus())) {
            return "inventory hold " + holdId + " is " + (hold == null ? "missing" : hold.getStatus());
        }
        if (hold.getExpiresAt() != null && !hold.getExpiresAt().isAfter(Instant.now())) {
            return "inventory hold " + holdId + " expired at " + hold.getExpiresAt();
        }
        return null;
    }

    private void failPayment(UUID bookingId, UUID holdId, String reason) {
        if (holdId != null) {
            releaseHoldQuietly(holdId, "payment failure");
        }
        txSupport.persistPaymentFailure(bookingId, reason);
    }

    private void releaseHoldQuietly(UUID holdId, String context) {
        try {
            inventoryClient.releaseHold(holdId);
        } catch (Exception ex) {
            // Not fatal: an unreleased hold is returned to stock by Inventory's expiry job.
            log.warn("Failed to release hold {} during {}: {}", holdId, context, ex.getMessage());
        }
    }

    private void refundQuietly(UUID paymentId, UUID bookingId) {
        try {
            paymentClient.refundPayment(paymentId, RefundRequest.builder()
                    .reason("Seats could not be confirmed for booking " + bookingId)
                    .build());
        } catch (Exception ex) {
            log.error("MANUAL ACTION REQUIRED: failed to refund payment {} for booking {} after hold "
                    + "confirmation failed: {}", paymentId, bookingId, ex.getMessage());
        }
    }

    private void validateSalesWindow(TicketTypeResponse ticketType) {
        // Assumes event-service and booking-service run in the same time zone,
        // since the sale window is exposed as a zone-less LocalDateTime.
        LocalDateTime now = LocalDateTime.now();
        if (ticketType.getSalesStartAt() != null && now.isBefore(ticketType.getSalesStartAt())) {
            throw new InvalidBookingStateException("Sales for ticket type " + ticketType.getId()
                    + " open at " + ticketType.getSalesStartAt());
        }
        if (ticketType.getSalesEndAt() != null && now.isAfter(ticketType.getSalesEndAt())) {
            throw new InvalidBookingStateException("Sales for ticket type " + ticketType.getId()
                    + " closed at " + ticketType.getSalesEndAt());
        }
    }

    private Booking requireSameOwner(Booking booking, UUID userId, UUID idempotencyKey) {
        // Without this, anyone who learns (or guesses a reused) key could read another user's booking.
        if (!booking.getUserId().equals(userId)) {
            throw new BookingAlreadyExistsException(idempotencyKey);
        }
        return booking;
    }

    private Booking findBooking(UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
    }

    private InvalidBookingStateException concurrentChange(UUID bookingId) {
        return new InvalidBookingStateException("Booking " + bookingId
                + " was modified by another request. Please reload and retry.");
    }

    private void validateBookingOwnership(Booking booking, String currentUserId, boolean isAdmin) {
        if (isAdmin) {
            return;
        }
        if (!booking.getUserId().toString().equals(currentUserId)) {
            throw new AccessDeniedException("You do not have permission to access this booking.");
        }
    }
}
