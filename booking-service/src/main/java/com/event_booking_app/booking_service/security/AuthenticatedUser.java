package com.event_booking_app.booking_service.security;

import java.security.Principal;

/**
 * Principal built from the access token. {@link #getName()} returns the user id, so
 * {@code Authentication.getName()} keeps returning the user id as before; the email
 * is carried along so it can be stored on bookings for notifications.
 */
public record AuthenticatedUser(String userId, String email) implements Principal {

    @Override
    public String getName() {
        return userId;
    }
}
