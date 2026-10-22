package com.event_booking_app.inventory_service.security;

import com.event_booking_app.inventory_service.dto.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTH_HEADER   = "Authorization";

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader(AUTH_HEADER);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(BEARER_PREFIX.length());

        if (!jwtService.isTokenValid(token)) {
            log.debug("Invalid JWT rejected for request: {} {}", request.getMethod(), request.getRequestURI());
            sendUnauthorizedError(response, request, "Invalid or expired JWT token");
            return;
        }

        String tokenType;
        try {
            tokenType = jwtService.extractTokenType(token);
        } catch (Exception e) {
            log.debug("Failed to extract token type: {}", e.getMessage());
            sendUnauthorizedError(response, request, "Malformed token claims");
            return;
        }

        // Refresh tokens are signed with the same key — reject them here so they
        // can't be used as long-lived access tokens.
        if (!JwtService.TYPE_ACCESS.equals(tokenType)) {
            log.debug("Rejected non-access token with type '{}' for request: {} {}",
                    tokenType, request.getMethod(), request.getRequestURI());
            sendUnauthorizedError(response, request, "Invalid token type. Only access tokens are permitted");
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                String userId = jwtService.extractSubject(token);
                String role   = jwtService.extractRole(token);

                if (userId == null || userId.isBlank() || role == null || role.isBlank()) {
                    log.debug("JWT missing required subject or role claim");
                    sendUnauthorizedError(response, request, "Token missing required subject or role claim");
                    return;
                }

                // Ensure "ROLE_" prefix for Spring Security authorities
                String authority = role.startsWith("ROLE_") ? role : "ROLE_" + role;
                List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(authority));

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null, authorities);

                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("Authenticated userId={} role={} for {} {}",
                        userId, role, request.getMethod(), request.getRequestURI());
            } catch (Exception e) {
                log.debug("Error extracting claims from token: {}", e.getMessage());
                sendUnauthorizedError(response, request, "Invalid token claims");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void sendUnauthorizedError(
            HttpServletResponse response,
            HttpServletRequest request,
            String message
    ) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        ErrorResponse errorResponse = ErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                message,
                request.getRequestURI()
        );

        objectMapper.writeValue(response.getOutputStream(), errorResponse);
    }
}
