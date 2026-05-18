package com.aipay.admin.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "secretKey",
            "test-secret-key-that-is-long-enough-for-hmac-sha256-32chars");
        ReflectionTestUtils.setField(provider, "accessTokenExpiryHours", 8L);
    }

    @Test
    void generateAndValidateAccessToken() {
        String token = provider.generateAccessToken(1L, 100L, 0, "orders:view");
        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.getOperatorId(token)).isEqualTo(1L);
    }

    @Test
    void invalidToken_returnsFalse() {
        assertThat(provider.validateToken("invalid.jwt.token")).isFalse();
    }

    @Test
    void expiredToken_returnsFalse() throws Exception {
        JwtTokenProvider shortLived = new JwtTokenProvider();
        ReflectionTestUtils.setField(shortLived, "secretKey",
            "test-secret-key-that-is-long-enough-for-hmac-sha256-32chars");
        ReflectionTestUtils.setField(shortLived, "accessTokenExpiryHours", 0L);
        String token = shortLived.generateAccessToken(1L, 100L, 0, "");
        Thread.sleep(10);
        assertThat(shortLived.validateToken(token)).isFalse();
    }
}
