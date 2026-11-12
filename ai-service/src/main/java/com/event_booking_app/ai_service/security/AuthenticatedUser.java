package com.event_booking_app.ai_service.security;

import com.event_booking_app.ai_service.exception.InvalidTokenException;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/** The authenticated caller: their ID and the token used to act on their behalf downstream. */
public record AuthenticatedUser(UUID id, String accessToken) {

    public static AuthenticatedUser from(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null) {
            throw new InvalidTokenException("JWT subject is missing");
        }
        try {
            return new AuthenticatedUser(UUID.fromString(subject), jwt.getTokenValue());
        } catch (IllegalArgumentException ex) {
            throw new InvalidTokenException("JWT subject is not a valid user id", ex);
        }
    }

    public String bearerToken() {
        return "Bearer " + accessToken;
    }

    /** Never print the token. */
    @Override
    public String toString() {
        return "AuthenticatedUser[id=" + id + ", accessToken=***]";
    }
}