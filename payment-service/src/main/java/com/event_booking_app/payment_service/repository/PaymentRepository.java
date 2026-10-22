package com.event_booking_app.payment_service.repository;

import com.event_booking_app.payment_service.entity.Payment;
import com.event_booking_app.payment_service.entity.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    // A booking can have several payment rows (e.g. a FAILED attempt then a SUCCESS),
    // so single-result lookups by booking must pick one explicitly.
    Optional<Payment> findFirstByBookingIdOrderByCreatedAtDesc(UUID bookingId);

    Optional<Payment> findFirstByBookingIdAndStatusInOrderByCreatedAtDesc(UUID bookingId, Collection<PaymentStatus> statuses);

    boolean existsByBookingIdAndStatusIn(UUID bookingId, Collection<PaymentStatus> statuses);

    Optional<Payment> findByIdempotencyKey(UUID idempotencyKey);

    boolean existsByIdempotencyKey(UUID idempotencyKey);

    // Serializes concurrent refunds of the same payment.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Takes a PostgreSQL transaction-scoped advisory lock keyed on the booking id, so
     * concurrent charges for the same booking run one at a time. Released automatically
     * at commit/rollback. Wrapped in COUNT(*) because pg_advisory_xact_lock returns void.
     */
    @Query(value = "SELECT COUNT(*) FROM (SELECT pg_advisory_xact_lock(hashtextextended(:bookingId, 0))) AS l",
            nativeQuery = true)
    long lockBooking(@Param("bookingId") String bookingId);
}
