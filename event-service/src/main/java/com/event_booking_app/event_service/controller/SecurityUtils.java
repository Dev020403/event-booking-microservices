package com.event_booking_app.event_service.controller;

import org.springframework.security.core.Authentication;

/**
 * Shared security utilities to avoid duplicating auth-checking logic across controllers.
 */
public final class SecurityUtils {

    private SecurityUtils() {
        // Utility class — no instances
    }

    /**
     * Returns {@code true} if the given authentication holds the {@code ROLE_ADMIN} authority.
     */
    public static boolean hasRoleAdmin(Authentication authentication) {
        if (authentication == null) return false;
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equalsIgnoreCase(authority.getAuthority()));
    }
}
