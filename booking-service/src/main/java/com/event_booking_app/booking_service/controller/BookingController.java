package com.event_booking_app.booking_service.controller;

import com.event_booking_app.booking_service.dto.BookingResponse;
import com.event_booking_app.booking_service.dto.ConfirmPaymentRequest;
import com.event_booking_app.booking_service.dto.CreateBookingRequest;
import com.event_booking_app.booking_service.entity.BookingStatus;
import com.event_booking_app.booking_service.security.SecurityUtils;
import com.event_booking_app.booking_service.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@Tag(name = "Booking Management", description = "APIs for creating, managing, paying, and cancelling ticket bookings")
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    @Operation(summary = "Create a new booking and hold inventory")
    public ResponseEntity<BookingResponse> createBooking(
            @Valid @RequestBody CreateBookingRequest request,
            Authentication authentication
    ) {
        String currentUserId = authentication.getName();
        log.info("REST request to create booking for user: {}", currentUserId);
        BookingResponse response = bookingService.createBooking(request, currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Confirm payment for a held booking")
    public ResponseEntity<BookingResponse> confirmBookingPayment(
            @PathVariable UUID id,
            @Valid @RequestBody ConfirmPaymentRequest request,
            Authentication authentication
    ) {
        String currentUserId = authentication.getName();
        log.info("REST request to confirm payment for bookingId: {} by user: {}", id, currentUserId);
        BookingResponse response = bookingService.confirmBookingPayment(id, request, currentUserId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel an existing booking")
    public ResponseEntity<BookingResponse> cancelBooking(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        String currentUserId = authentication.getName();
        boolean isAdmin = SecurityUtils.hasRoleAdmin(authentication);
        log.info("REST request to cancel bookingId: {} by user: {}, isAdmin: {}", id, currentUserId, isAdmin);
        BookingResponse response = bookingService.cancelBooking(id, currentUserId, isAdmin);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get booking details by ID")
    public ResponseEntity<BookingResponse> getBookingById(
            @PathVariable UUID id,
            Authentication authentication
    ) {
        String currentUserId = authentication.getName();
        boolean isAdmin = SecurityUtils.hasRoleAdmin(authentication);
        BookingResponse response = bookingService.getBookingById(id, currentUserId, isAdmin);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my")
    @Operation(summary = "Get bookings of the authenticated user")
    public ResponseEntity<Page<BookingResponse>> getMyBookings(
            @RequestParam(required = false) BookingStatus status,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable,
            Authentication authentication
    ) {
        String currentUserId = authentication.getName();
        Page<BookingResponse> response = bookingService.getUserBookings(currentUserId, status, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "Get bookings (all bookings for admin, or user bookings for regular user)")
    public ResponseEntity<Page<BookingResponse>> getBookings(
            @RequestParam(required = false) BookingStatus status,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable,
            Authentication authentication
    ) {
        String currentUserId = authentication.getName();
        boolean isAdmin = SecurityUtils.hasRoleAdmin(authentication);

        Page<BookingResponse> response;
        if (isAdmin) {
            response = bookingService.getAllBookings(status, pageable);
        } else {
            response = bookingService.getUserBookings(currentUserId, status, pageable);
        }
        return ResponseEntity.ok(response);
    }
}
