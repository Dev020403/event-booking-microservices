package com.event_booking_app.inventory_service.config;

import com.event_booking_app.inventory_service.security.CustomAccessDeniedHandler;
import com.event_booking_app.inventory_service.security.CustomAuthenticationEntryPoint;
import com.event_booking_app.inventory_service.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
    private final CustomAccessDeniedHandler customAccessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(customAuthenticationEntryPoint)
                        .accessDeniedHandler(customAccessDeniedHandler)
                )
                .authorizeHttpRequests(auth -> auth
                        // Public: availability checks (e.g. "3 tickets left" on a frontend)
                        .requestMatchers(HttpMethod.GET, "/api/v1/inventory/**").permitAll()
                        .requestMatchers("/actuator/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/error").permitAll()

                        // Manual inventory creation: ADMIN only.
                        // (The primary creation path is the Kafka listener reacting to
                        // TicketTypeCreatedEvent — this endpoint is a fallback/override.)
                        .requestMatchers(HttpMethod.POST, "/api/v1/inventory").hasRole("ADMIN")

                        // Hold / confirm / release: any authenticated caller.
                        // In practice this means Booking Service's service-to-service
                        // calls, so no specific role is required — just a valid JWT.
                        .requestMatchers(HttpMethod.POST, "/api/v1/inventory/*/hold").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/holds/*/confirm").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/holds/*/release").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/holds/**").authenticated()

                        // Everything else requires authentication
                        .anyRequest().authenticated()

                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}