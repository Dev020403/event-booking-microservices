package com.event_booking_app.api_gateway.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.net.InetSocketAddress;

@Configuration
public class RateLimiterConfig {

    private final SecretKey signingKey;

    public RateLimiterConfig(@Value("${jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    /** For public / unauthenticated routes (login, event browsing). */
    @Bean
    @Primary
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just("ip:" + clientIp(exchange));
    }

    /** For authenticated routes: limit per user, fall back to IP if no valid token. */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (auth != null && auth.startsWith("Bearer ")) {
                try {
                    String userId = Jwts.parser().verifyWith(signingKey).build()
                            .parseSignedClaims(auth.substring(7))
                            .getPayload().getSubject();
                    if (userId != null) {
                        return Mono.just("user:" + userId);
                    }
                } catch (Exception ignored) {
                    // invalid/expired token: the downstream service will reject it; limit by IP here
                }
            }
            return Mono.just("ip:" + clientIp(exchange));
        };
    }

    private String clientIp(ServerWebExchange exchange) {
        InetSocketAddress addr = exchange.getRequest().getRemoteAddress();
        return (addr != null && addr.getAddress() != null)
                ? addr.getAddress().getHostAddress() : "unknown";
    }
}