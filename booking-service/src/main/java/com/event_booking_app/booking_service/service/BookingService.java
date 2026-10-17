package com.event_booking_app.booking_service.service;

import com.event_booking_app.booking_service.dto.BookingResponse;
import com.event_booking_app.booking_service.dto.ConfirmPaymentRequest;
import com.event_booking_app.booking_service.dto.CreateBookingRequest;
import com.event_booking_app.booking_service.entity.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface BookingService {
    BookingResponse createBooking(CreateBookingRequest request, String currentUserId);

    BookingResponse confirmBookingPayment(UUID bookingId, ConfirmPaymentRequest request, String currentUserId);

    BookingResponse cancelBooking(UUID bookingId, String currentUserId, boolean isAdmin);

    BookingResponse getBookingById(UUID bookingId, String currentUserId, boolean isAdmin);

    Page<BookingResponse> getUserBookings(String currentUserId, BookingStatus status, Pageable pageable);

    Page<BookingResponse> getAllBookings(BookingStatus status, Pageable pageable);
}
