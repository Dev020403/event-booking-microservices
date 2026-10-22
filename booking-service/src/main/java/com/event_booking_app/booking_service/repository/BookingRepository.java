package com.event_booking_app.booking_service.repository;
import com.event_booking_app.booking_service.entity.Booking;
import com.event_booking_app.booking_service.entity.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {
    Optional<Booking> findByIdempotencyKey(UUID idempotencyKey);

    Page<Booking> findByUserId(UUID userId, Pageable pageable);

    Page<Booking> findByUserIdAndStatus(UUID userId, BookingStatus status, Pageable pageable);

    Page<Booking> findByStatus(BookingStatus status, Pageable pageable);

    Optional<Booking> findByHoldId(UUID holdId);

    java.util.List<Booking> findByEventId(UUID eventId);

    /**
     * Atomic compare-and-set on status. Returns 1 only for the single caller that
     * observed {@code from}; concurrent callers get 0. Used to stop two requests from
     * paying for (or cancelling) the same booking at once.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Booking b SET b.status = :to, b.updatedAt = :now WHERE b.id = :id AND b.status = :from")
    int transitionStatus(@Param("id") UUID id,
                         @Param("from") BookingStatus from,
                         @Param("to") BookingStatus to,
                         @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Booking b SET b.status = :to, b.paymentId = :paymentId, b.updatedAt = :now "
            + "WHERE b.id = :id AND b.status = :from")
    int transitionStatusWithPayment(@Param("id") UUID id,
                                    @Param("from") BookingStatus from,
                                    @Param("to") BookingStatus to,
                                    @Param("paymentId") UUID paymentId,
                                    @Param("now") Instant now);
}
