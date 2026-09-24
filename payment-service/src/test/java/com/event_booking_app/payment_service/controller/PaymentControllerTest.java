package com.event_booking_app.payment_service.controller;

import com.event_booking_app.payment_service.dto.PaymentResponse;
import com.event_booking_app.payment_service.dto.ProcessPaymentRequest;
import com.event_booking_app.payment_service.entity.PaymentMethod;
import com.event_booking_app.payment_service.entity.PaymentStatus;
import com.event_booking_app.payment_service.exception.PaymentNotFoundException;
import com.event_booking_app.payment_service.security.JwtService;
import com.event_booking_app.payment_service.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentService paymentService;

    @MockBean
    private JwtService jwtService;

    @Test
    @DisplayName("POST /api/v1/payments/process - should return 201 Created on valid request")
    void processPayment_Success() throws Exception {
        UUID bookingId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        ProcessPaymentRequest request = ProcessPaymentRequest.builder()
                .bookingId(bookingId)
                .amount(new BigDecimal("150.00"))
                .currency("USD")
                .paymentMethod(PaymentMethod.CARD)
                .idempotencyKey(idempotencyKey)
                .build();

        PaymentResponse response = PaymentResponse.builder()
                .id(paymentId)
                .bookingId(bookingId)
                .amount(new BigDecimal("150.00"))
                .currency("USD")
                .paymentMethod(PaymentMethod.CARD)
                .status(PaymentStatus.SUCCESS)
                .providerTransactionId("CARD-12345")
                .idempotencyKey(idempotencyKey)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(paymentService.processPayment(any(ProcessPaymentRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/payments/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.providerTransactionId").value("CARD-12345"));
    }

    @Test
    @DisplayName("POST /api/v1/payments/process - should return 400 Bad Request when mandatory fields missing")
    void processPayment_ValidationError() throws Exception {
        ProcessPaymentRequest invalidRequest = ProcessPaymentRequest.builder()
                .amount(new BigDecimal("0.00")) // Invalid amount <= 0
                .build();

        mockMvc.perform(post("/api/v1/payments/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/payments/{id} - should return 200 OK")
    void getPaymentById_Success() throws Exception {
        UUID paymentId = UUID.randomUUID();

        PaymentResponse response = PaymentResponse.builder()
                .id(paymentId)
                .bookingId(UUID.randomUUID())
                .amount(new BigDecimal("200.00"))
                .currency("USD")
                .paymentMethod(PaymentMethod.UPI)
                .status(PaymentStatus.SUCCESS)
                .build();

        when(paymentService.getPaymentById(paymentId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/payments/{id}", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.paymentMethod").value("UPI"));
    }

    @Test
    @DisplayName("GET /api/v1/payments/{id} - should return 404 Not Found when non-existent")
    void getPaymentById_NotFound() throws Exception {
        UUID paymentId = UUID.randomUUID();

        when(paymentService.getPaymentById(paymentId)).thenThrow(new PaymentNotFoundException(paymentId));

        mockMvc.perform(get("/api/v1/payments/{id}", paymentId))
                .andExpect(status().isNotFound());
    }
}
