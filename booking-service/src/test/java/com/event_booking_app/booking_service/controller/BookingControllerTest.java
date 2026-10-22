package com.event_booking_app.booking_service.controller;

import com.event_booking_app.booking_service.client.dto.PaymentMethod;
import com.event_booking_app.booking_service.dto.BookingResponse;
import com.event_booking_app.booking_service.dto.ConfirmPaymentRequest;
import com.event_booking_app.booking_service.dto.CreateBookingRequest;
import com.event_booking_app.booking_service.entity.BookingStatus;
import com.event_booking_app.booking_service.service.BookingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class BookingControllerTest {

    private MockMvc mockMvc;

    @Mock
    private BookingService bookingService;

    @InjectMocks
    private BookingController bookingController;

    private ObjectMapper objectMapper = new ObjectMapper();

    private UUID userId;
    private UUID bookingId;
    private Authentication userAuth;
    private Authentication adminAuth;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(bookingController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();

        userId = UUID.randomUUID();
        bookingId = UUID.randomUUID();

        userAuth = new UsernamePasswordAuthenticationToken(
                userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        adminAuth = new UsernamePasswordAuthenticationToken(
                userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
    }

    @Test
    void createBooking_returnsCreated() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();

        CreateBookingRequest request = CreateBookingRequest.builder()
                .eventId(eventId)
                .ticketTypeId(ticketTypeId)
                .quantity(2)
                .idempotencyKey(idempotencyKey)
                .build();

        BookingResponse response = BookingResponse.builder()
                .id(bookingId)
                .userId(userId)
                .eventId(eventId)
                .ticketTypeId(ticketTypeId)
                .quantity(2)
                .totalAmount(new BigDecimal("100.00"))
                .status(BookingStatus.HELD)
                .build();

        when(bookingService.createBooking(any(CreateBookingRequest.class), eq(userId.toString()), isNull()))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/bookings")
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(bookingId.toString()))
                .andExpect(jsonPath("$.status").value("HELD"))
                .andExpect(jsonPath("$.quantity").value(2));
    }

    @Test
    void confirmBookingPayment_returnsOk() throws Exception {
        ConfirmPaymentRequest request = ConfirmPaymentRequest.builder()
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .idempotencyKey(UUID.randomUUID())
                .build();

        BookingResponse response = BookingResponse.builder()
                .id(bookingId)
                .userId(userId)
                .status(BookingStatus.CONFIRMED)
                .build();

        when(bookingService.confirmBookingPayment(eq(bookingId), any(ConfirmPaymentRequest.class), eq(userId.toString())))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/bookings/{id}/confirm", bookingId)
                        .principal(userAuth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId.toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void cancelBooking_returnsOk() throws Exception {
        BookingResponse response = BookingResponse.builder()
                .id(bookingId)
                .userId(userId)
                .status(BookingStatus.CANCELLED)
                .build();

        when(bookingService.cancelBooking(eq(bookingId), eq(userId.toString()), eq(false)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/bookings/{id}/cancel", bookingId)
                        .principal(userAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId.toString()))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void getBookingById_returnsOk() throws Exception {
        BookingResponse response = BookingResponse.builder()
                .id(bookingId)
                .userId(userId)
                .status(BookingStatus.CONFIRMED)
                .build();

        when(bookingService.getBookingById(eq(bookingId), eq(userId.toString()), eq(false)))
                .thenReturn(response);

        mockMvc.perform(get("/api/v1/bookings/{id}", bookingId)
                        .principal(userAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookingId.toString()));
    }

    @Test
    void getMyBookings_returnsPage() throws Exception {
        BookingResponse response = BookingResponse.builder()
                .id(bookingId)
                .userId(userId)
                .status(BookingStatus.CONFIRMED)
                .build();
        Page<BookingResponse> page = new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1);

        when(bookingService.getUserBookings(eq(userId.toString()), eq(null), any()))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/bookings/my")
                        .principal(userAuth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(bookingId.toString()));
    }
}
