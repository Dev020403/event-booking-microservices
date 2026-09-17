package com.event_booking_app.user_service.service;

import com.event_booking_app.user_service.dto.AuthResponse;
import com.event_booking_app.user_service.dto.LoginRequest;
import com.event_booking_app.user_service.entity.User;
import com.event_booking_app.user_service.exception.InvalidTokenException;
import com.event_booking_app.user_service.exception.UserNotFoundException;
import com.event_booking_app.user_service.repository.UserRepository;
import com.event_booking_app.user_service.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository        userRepository;
    private final JwtService            jwtService;

    @Override
    public AuthResponse login(LoginRequest request) {
        String email = request.email().toLowerCase().trim();
        log.debug("Login attempt for email: {}", email);

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password())
        );

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));

        String accessToken  = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole());
        String refreshToken = jwtService.generateRefreshToken(user.getId());

        log.info("Login successful for userId={}", user.getId());
        return AuthResponse.of(accessToken, refreshToken, jwtService.getAccessTokenExpirySeconds());
    }

    @Override
    public AuthResponse refresh(String refreshToken) {
        log.debug("Token refresh requested");

        if (!jwtService.isTokenValid(refreshToken)) {
            throw new InvalidTokenException("Refresh token is invalid or expired");
        }

        if (!JwtService.TYPE_REFRESH.equals(jwtService.extractTokenType(refreshToken))) {
            throw new InvalidTokenException("Provided token is not a refresh token");
        }

        UUID userId = UUID.fromString(jwtService.extractSubject(refreshToken));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        String newAccessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), user.getRole());

        log.info("Access token refreshed for userId={}", userId);
        return AuthResponse.of(newAccessToken, refreshToken, jwtService.getAccessTokenExpirySeconds());
    }
}
