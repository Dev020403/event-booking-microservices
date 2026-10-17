package com.event_booking_app.booking_service.repository;
import com.event_booking_app.booking_service.entity.Booking;
import com.event_booking_app.booking_service.entity.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {
    Optional<Booking> findByIdempotencyKey(UUID idempotencyKey);

    Page<Booking> findByUserId(UUID userId, Pageable pageable);

    Page<Booking> findByUserIdAndStatus(UUID userId, BookingStatus status, Pageable pageable);

    Page<Booking> findByStatus(BookingStatus status, Pageable pageable);
}