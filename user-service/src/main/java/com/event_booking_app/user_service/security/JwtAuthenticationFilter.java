package com.event_booking_app.user_service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader(AUTH_HEADER);

        // No Authorization header or not Bearer → pass through (unauthenticated)
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(BEARER_PREFIX.length());

        // Validate signature + expiry; reject silently (let Spring Security handle 401)
        if (!jwtService.isTokenValid(token)) {
            log.debug("Invalid JWT rejected for request: {} {}", request.getMethod(), request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        // Skip refresh tokens — they must only be presented to /auth/refresh
        if (JwtService.TYPE_REFRESH.equals(jwtService.extractTokenType(token))) {
            log.debug("Refresh token rejected on non-refresh endpoint");
            filterChain.doFilter(request, response);
            return;
        }

        // Only set authentication if the context is not already populated
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String userId = jwtService.extractSubject(token);
            String role   = jwtService.extractRole(token).name();

            // Build granted authority — Spring Security expects "ROLE_" prefix
            List<SimpleGrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_" + role)
            );

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);

            authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request)
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("Authenticated userId={} role={} for {} {}",
                    userId, role, request.getMethod(), request.getRequestURI());
        }

        filterChain.doFilter(request, response);
    }
}
