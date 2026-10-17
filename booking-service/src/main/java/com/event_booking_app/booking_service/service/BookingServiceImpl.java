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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final BookingMapper bookingMapper;
    private final EventClient eventClient;
    private final InventoryClient inventoryClient;
    private final PaymentClient paymentClient;
    private final BookingEventPublisher eventPublisher;

    @Value("${payment.default-currency:INR}")
    private String defaultCurrency;

    @Override
    @Transactional
    public BookingResponse createBooking(CreateBookingRequest request, String currentUserId) {
        log.info("Creating booking for userId={}, eventId={}, ticketTypeId={}, quantity={}",
                currentUserId, request.getEventId(), request.getTicketTypeId(), request.getQuantity());

        // 1. Check idempotency
        if (request.getIdempotencyKey() != null) {
            Optional<Booking> existing = bookingRepository.findByIdempotencyKey(request.getIdempotencyKey());
            if (existing.isPresent()) {
                log.info("Found existing booking with idempotency key: {}", request.getIdempotencyKey());
                return bookingMapper.toResponse(existing.get());
            }
        }

        // 2. Fetch ticket type from Event Service to verify existence & price
        UUID userId = UUID.fromString(currentUserId);
        TicketTypeResponse ticketType = eventClient.getTicketTypeById(request.getTicketTypeId());
        if (ticketType == null) {
            throw new InvalidBookingStateException("Invalid ticket type ID: " + request.getTicketTypeId());
        }
        if (!request.getEventId().equals(ticketType.getEventId())) {
            throw new InvalidBookingStateException("Ticket type " + request.getTicketTypeId() + " does not belong to event " + request.getEventId());
        }

        // 3. Fetch Inventory ID from Inventory Service
        InventoryResponse inventory = inventoryClient.getInventory(request.getEventId(), request.getTicketTypeId());
        if (inventory == null) {
            throw new InvalidBookingStateException("No inventory found for eventId=" + request.getEventId() + " ticketTypeId=" + request.getTicketTypeId());
        }

        // 4. Reserve/Hold seats via Inventory Service
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

        BigDecimal unitPrice = ticketType.getPrice();
        BigDecimal totalAmount = unitPrice.multiply(BigDecimal.valueOf(request.getQuantity()));
        // Bug 4: derive currency from ticket type; fall back to configurable default
        String currency = (ticketType.getCurrency() != null && !ticketType.getCurrency().isBlank())
                ? ticketType.getCurrency()
                : defaultCurrency;

        // 5. Create initial Booking record
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

        // Bug 3: if saving the booking fails, release the hold as a compensating action
        Booking savedBooking;
        try {
            savedBooking = bookingRepository.save(booking);
        } catch (Exception ex) {
            log.error("Failed to persist booking after holding inventory {}. Releasing hold.", holdResponse.getHoldId());
            try {
                inventoryClient.releaseHold(holdResponse.getHoldId());
            } catch (Exception releaseEx) {
                log.error("Failed to release hold {} during booking save rollback: {}", holdResponse.getHoldId(), releaseEx.getMessage());
            }
            throw new InvalidBookingStateException("Failed to create booking: " + ex.getMessage());
        }

        // 6. If payment method provided, process payment immediately
        if (request.getPaymentMethod() != null) {
            processPaymentAndFinalizeBooking(savedBooking, request.getPaymentMethod(), request.getIdempotencyKey(), currency);
        } else {
            // Publish created event for downstream notification
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
        }
        return bookingMapper.toResponse(savedBooking);
    }

    @Override
    @Transactional
    public BookingResponse confirmBookingPayment(UUID bookingId, ConfirmPaymentRequest request, String currentUserId) {
        log.info("Confirming payment for bookingId: {}", bookingId);

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        validateBookingOwnership(booking, currentUserId, false);

        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            return bookingMapper.toResponse(booking);
        }
        if (booking.getStatus() != BookingStatus.HELD && booking.getStatus() != BookingStatus.PENDING) {
            throw new InvalidBookingStateException("Booking cannot be paid. Current status: " + booking.getStatus());
        }
        // Derive currency stored on booking, fall back to default
        String currency = (booking.getCurrency() != null && !booking.getCurrency().isBlank())
                ? booking.getCurrency()
                : defaultCurrency;
        processPaymentAndFinalizeBooking(booking, request.getPaymentMethod(), request.getIdempotencyKey(), currency);

        return bookingMapper.toResponse(booking);
    }

    @Override
    @Transactional
    public BookingResponse cancelBooking(UUID bookingId, String currentUserId, boolean isAdmin) {
        log.info("Cancelling bookingId: {} requested by userId: {}, isAdmin: {}", bookingId, currentUserId, isAdmin);

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        validateBookingOwnership(booking, currentUserId, isAdmin);

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return bookingMapper.toResponse(booking);
        }
        if (booking.getStatus() == BookingStatus.EXPIRED || booking.getStatus() == BookingStatus.PAYMENT_FAILED) {
            throw new InvalidBookingStateException("Cannot cancel booking with status: " + booking.getStatus());
        }

        // If booking was CONFIRMED and paid, initiate refund
        if (booking.getStatus() == BookingStatus.CONFIRMED && booking.getPaymentId() != null) {
            try {
                paymentClient.refundPayment(booking.getPaymentId(), RefundRequest.builder()
                        .reason("Booking cancelled by " + (isAdmin ? "admin" : "user"))
                        .build());
            } catch (Exception ex) {
                log.error("Failed to refund payment for booking {}: {}", booking.getId(), ex.getMessage());
                throw new InvalidBookingStateException("Failed to refund payment: " + ex.getMessage());
            }
        }

        // If booking held inventory, release hold
        if (booking.getStatus() == BookingStatus.HELD && booking.getHoldId() != null) {
            try {
                inventoryClient.releaseHold(booking.getHoldId());
            } catch (Exception ex) {
                log.warn("Failed to release hold {} during cancellation: {}", booking.getHoldId(), ex.getMessage());
            }
        }

        booking.setStatus(BookingStatus.CANCELLED);
        Booking saved = bookingRepository.save(booking);

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
    public BookingResponse getBookingById(UUID bookingId, String currentUserId, boolean isAdmin) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        validateBookingOwnership(booking, currentUserId, isAdmin);
        return bookingMapper.toResponse(booking);
    }

    @Override
    public Page<BookingResponse> getUserBookings(String currentUserId, BookingStatus status, Pageable pageable) {
        UUID userId = UUID.fromString(currentUserId);
        Page<Booking> page = (status != null)
                ? bookingRepository.findByUserIdAndStatus(userId, status, pageable)
                : bookingRepository.findByUserId(userId, pageable);
        return page.map(bookingMapper::toResponse);
    }

    @Override
    public Page<BookingResponse> getAllBookings(BookingStatus status, Pageable pageable) {
        Page<Booking> page = (status != null)
                ? bookingRepository.findByStatus(status, pageable)
                : bookingRepository.findAll(pageable);
        return page.map(bookingMapper::toResponse);
    }

    private void processPaymentAndFinalizeBooking(Booking booking, PaymentMethod paymentMethod, UUID idempotencyKey, String currency) {
        // Bug 4: use currency from ticket type / booking instead of hardcoded "USD"
        ProcessPaymentRequest paymentRequest = ProcessPaymentRequest.builder()
                .bookingId(booking.getId())
                .amount(booking.getTotalAmount())
                .currency(currency)
                .paymentMethod(paymentMethod)
                .idempotencyKey(idempotencyKey != null ? idempotencyKey : UUID.randomUUID())
                .build();
        try {
            PaymentResponse paymentResponse = paymentClient.processPayment(paymentRequest);
            // Bug 1 fix: compare against SUCCESS (was COMPLETED, which never matched)
            if (paymentResponse != null && paymentResponse.getStatus() == PaymentStatus.SUCCESS) {
                // Confirm inventory hold
                if (booking.getHoldId() != null) {
                    try {
                        inventoryClient.confirmHold(booking.getHoldId());
                    } catch (Exception e) {
                        log.error("Failed to confirm inventory hold {}: {}", booking.getHoldId(), e.getMessage());
                        // Bug 2 fix: persist PAYMENT_FAILED in a new transaction so it survives the outer rollback
                        persistPaymentFailure(booking, "Failed to confirm inventory hold: " + e.getMessage());
                        throw new InvalidBookingStateException("Hold confirmation failed: " + e.getMessage());
                    }
                }
                booking.setStatus(BookingStatus.CONFIRMED);
                booking.setPaymentId(paymentResponse.getId());
                bookingRepository.save(booking);

                // Publish BookingConfirmedEvent
                eventPublisher.publishBookingConfirmed(BookingConfirmedEvent.builder()
                        .bookingId(booking.getId())
                        .userId(booking.getUserId())
                        .eventId(booking.getEventId())
                        .ticketTypeId(booking.getTicketTypeId())
                        .quantity(booking.getQuantity())
                        .totalAmount(booking.getTotalAmount())
                        .paymentId(paymentResponse.getId())
                        .confirmedAt(Instant.now())
                        .build());
                log.info("Booking {} successfully CONFIRMED with payment {}", booking.getId(), paymentResponse.getId());
            } else {
                // Bug 2 fix: persist failure in REQUIRES_NEW before throwing
                persistPaymentFailure(booking, "Payment returned failure status");
                throw new BookingPaymentFailedException("Payment processing returned unsuccessful status");
            }
        } catch (BookingPaymentFailedException | InvalidBookingStateException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Payment processing failed for booking {}: {}", booking.getId(), ex.getMessage());
            persistPaymentFailure(booking, ex.getMessage());
            throw new BookingPaymentFailedException("Payment processing failed: " + ex.getMessage());
        }
    }

    private void validateBookingOwnership(Booking booking, String currentUserId, boolean isAdmin) {
        if (isAdmin) {
            return;
        }
        if (!booking.getUserId().toString().equals(currentUserId)) {
            throw new AccessDeniedException("You do not have permission to access this booking.");
        }
    }

    /**
     * Bug 2 fix: runs in its own transaction (REQUIRES_NEW) so the PAYMENT_FAILED
     * status is committed to the DB even when the outer @Transactional rolls back
     * due to an exception thrown after calling this method.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistPaymentFailure(Booking booking, String reason) {
        log.warn("Persisting PAYMENT_FAILED for bookingId={}, reason={}", booking.getId(), reason);
        // Compensating action: Release hold in Inventory Service
        if (booking.getHoldId() != null) {
            try {
                inventoryClient.releaseHold(booking.getHoldId());
            } catch (Exception e) {
                log.error("Failed to release hold {} during payment failure compensating action: {}", booking.getHoldId(), e.getMessage());
            }
        }
        // Re-fetch to avoid stale state in nested transaction
        bookingRepository.findById(booking.getId()).ifPresent(b -> {
            b.setStatus(BookingStatus.PAYMENT_FAILED);
            bookingRepository.save(b);
        });
    }
}
