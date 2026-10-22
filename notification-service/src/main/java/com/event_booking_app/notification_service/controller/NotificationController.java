package com.event_booking_app.notification_service.controller;

import com.event_booking_app.notification_service.dto.MarkAllReadResponse;
import com.event_booking_app.notification_service.dto.NotificationResponse;
import com.event_booking_app.notification_service.dto.UnreadCountResponse;
import com.event_booking_app.notification_service.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app notifications for the authenticated user")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "List the current user's notifications, newest first")
    public ResponseEntity<PagedModel<NotificationResponse>> getNotifications(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication
    ) {
        Page<NotificationResponse> page =
                notificationService.getNotifications(currentUserId(authentication), unreadOnly, pageable);
        // PagedModel gives a stable JSON shape ({content, page:{...}}); serializing
        // PageImpl directly is unsupported and its shape can change between versions.
        return ResponseEntity.ok(new PagedModel<>(page));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Number of unread notifications for the current user")
    public ResponseEntity<UnreadCountResponse> getUnreadCount(Authentication authentication) {
        return ResponseEntity.ok(new UnreadCountResponse(notificationService.getUnreadCount(currentUserId(authentication))));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark one of the current user's notifications as read")
    public ResponseEntity<NotificationResponse> markRead(@PathVariable UUID id, Authentication authentication) {
        return ResponseEntity.ok(notificationService.markRead(id, currentUserId(authentication)));
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Mark all of the current user's notifications as read")
    public ResponseEntity<MarkAllReadResponse> markAllRead(Authentication authentication) {
        return ResponseEntity.ok(new MarkAllReadResponse(notificationService.markAllRead(currentUserId(authentication))));
    }

    private static UUID currentUserId(Authentication authentication) {
        return UUID.fromString(authentication.getName());
    }
}
