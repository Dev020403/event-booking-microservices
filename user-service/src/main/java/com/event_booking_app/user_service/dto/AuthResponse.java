package com.event_booking_app.user_service.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        long accessTokenExpiry,
        String tokenType
) {
    public static AuthResponse of(String accessToken, String refreshToken, long expirySeconds) {
        return new AuthResponse(accessToken, refreshToken, expirySeconds, "Bearer");
    }
}
