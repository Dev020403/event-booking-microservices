package com.event_booking_app.notification_service.controller;

import com.event_booking_app.notification_service.dto.NotificationResponse;
import com.event_booking_app.notification_service.entity.EmailStatus;
import com.event_booking_app.notification_service.entity.NotificationType;
import com.event_booking_app.notification_service.exception.GlobalExceptionHandler;
import com.event_booking_app.notification_service.exception.NotificationNotFoundException;
import com.event_booking_app.notification_service.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationService notificationService;

    private MockMvc mockMvc;
    private final UUID userId = UUID.randomUUID();
    private Authentication auth;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new NotificationController(notificationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
        auth = new UsernamePasswordAuthenticationToken(
                userId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private NotificationResponse response(UUID id) {
        return new NotificationResponse(id, NotificationType.BOOKING_CONFIRMED, "Booking confirmed", "msg",
                UUID.randomUUID(), false, null, EmailStatus.SENT, Instant.now());
    }

    @Test
    void list_returnsCurrentUsersNotifications() throws Exception {
        UUID id = UUID.randomUUID();
        when(notificationService.getNotifications(eq(userId), eq(true), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(response(id)), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/notifications").param("unreadOnly", "true").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(id.toString()))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void unreadCount_returnsCount() throws Exception {
        when(notificationService.getUnreadCount(userId)).thenReturn(3L);

        mockMvc.perform(get("/api/v1/notifications/unread-count").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3));
    }

    @Test
    void markRead_unknownOrForeignId_is404() throws Exception {
        UUID id = UUID.randomUUID();
        when(notificationService.markRead(id, userId)).thenThrow(new NotificationNotFoundException(id));

        mockMvc.perform(patch("/api/v1/notifications/{id}/read", id).principal(auth))
                .andExpect(status().isNotFound());
    }

    @Test
    void markRead_invalidId_is400() throws Exception {
        mockMvc.perform(patch("/api/v1/notifications/{id}/read", "not-a-uuid").principal(auth))
                .andExpect(status().isBadRequest());
    }

    @Test
    void markAllRead_returnsUpdatedCount() throws Exception {
        when(notificationService.markAllRead(userId)).thenReturn(4);

        mockMvc.perform(patch("/api/v1/notifications/read-all").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(4));
    }
}
