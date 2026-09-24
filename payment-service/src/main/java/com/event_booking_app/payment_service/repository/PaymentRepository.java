package com.event_booking_app.payment_service.repository;

import com.event_booking_app.payment_service.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByBookingId(UUID bookingId);

    Optional<Payment> findByIdempotencyKey(UUID idempotencyKey);

    boolean existsByIdempotencyKey(UUID idempotencyKey);
}
