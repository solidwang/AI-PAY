package com.aipay.admin.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${app.jwt.secret-key}")
    private String secretKey;

    @Value("${app.jwt.access-token-expiry-hours:8}")
    private long accessTokenExpiryHours;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(Long operatorId, Long merchantId,
                                       int isAdmin, String permissions) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpiryHours * 3600 * 1000);

        return Jwts.builder()
            .subject(operatorId.toString())
            .claim("merchant_id", merchantId)
            .claim("is_admin", isAdmin)
            .claim("permissions", permissions)
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key())
            .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().verifyWith(key()).build().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT: {}", e.getMessage());
            return false;
        }
    }

    public Long getOperatorId(String token) {
        Claims claims = Jwts.parser().verifyWith(key()).build()
            .parseSignedClaims(token).getPayload();
        return Long.parseLong(claims.getSubject());
    }

    public Long getMerchantId(String token) {
        Claims claims = Jwts.parser().verifyWith(key()).build()
            .parseSignedClaims(token).getPayload();
        return claims.get("merchant_id", Long.class);
    }
}
