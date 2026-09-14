package com.event_booking_app.user_service.security;

import com.event_booking_app.user_service.config.JwtProperties;
import com.event_booking_app.user_service.entity.Role;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {
    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_ROLE  = "role";
    public static final String CLAIM_TYPE  = "tokenType";
    public static final String TYPE_ACCESS  = "ACCESS";
    public static final String TYPE_REFRESH = "REFRESH";

    private final JwtProperties jwtProperties;

    public String generateAccessToken(UUID userId, String email, Role role) {
        return buildToken(
                userId,
                Map.of(CLAIM_EMAIL, email, CLAIM_ROLE, role.name(), CLAIM_TYPE, TYPE_ACCESS),
                jwtProperties.getAccessTokenExpiryMs()
        );
    }

    public String generateRefreshToken(UUID userId) {
        return buildToken(
                userId,
                Map.of(CLAIM_TYPE, TYPE_REFRESH),
                jwtProperties.getRefreshTokenExpiryMs()
        );
    }

    public String extractSubject(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractEmail(String token) {
        return extractClaim(token, claims -> claims.get(CLAIM_EMAIL, String.class));
    }

    public Role extractRole(String token) {
        String roleStr = extractClaim(token, claims -> claims.get(CLAIM_ROLE, String.class));
        return Role.valueOf(roleStr);
    }

    public String extractTokenType(String token) {
        return extractClaim(token, claims -> claims.get(CLAIM_TYPE, String.class));
    }

    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("JWT unsupported: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("JWT malformed: {}", e.getMessage());
        } catch (SecurityException e) {
            log.warn("JWT signature invalid: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT claims empty: {}", e.getMessage());
        }
        return false;
    }

    public boolean isTokenExpired(String token) {
        try {
            Date expiry = extractClaim(token, Claims::getExpiration);
            return expiry.before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        }
    }

    public long getAccessTokenExpirySeconds() {
        return jwtProperties.getAccessTokenExpiryMs() / 1000;
    }

    private String buildToken(UUID userId, Map<String, Object> extraClaims, long expiryMs) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .claims(extraClaims)
                .subject(userId.toString())
                .issuedAt(new Date(now))
                .expiration(new Date(now + expiryMs))
                .signWith(signingKey(), Jwts.SIG.HS256)
                .compact();
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(parseClaims(token));
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey signingKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtProperties.getSecret());
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
