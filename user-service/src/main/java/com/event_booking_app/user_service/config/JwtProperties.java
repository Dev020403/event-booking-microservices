package com.event_booking_app.user_service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    private String secret;

    private long accessTokenExpiryMs = 900_000L;

    private long refreshTokenExpiryMs = 604_800_000L;
}
