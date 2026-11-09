package com.event_booking_app.ai_service.security;

import com.event_booking_app.ai_service.exception.InvalidTokenException;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

public final class CurrentUser {

    private CurrentUser() {
    }

    /** Extracts the authenticated user's ID from the JWT subject claim. */
    public static UUID id(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null) {
            throw new InvalidTokenException("JWT subject is missing");
        }
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException ex) {
            throw new InvalidTokenException("JWT subject is not a valid user id", ex);
        }
    }
}