package com.medictech.auth_service.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilsTest {

    private static final String SECRET = "test-secret-key-must-be-at-least-256-bits-long-1234567890";

    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(jwtUtils, "jwtExpiration", 60_000L);
    }

    @Test
    void generatedToken_containsEmailAndRole() {
        String token = jwtUtils.generateToken("ana@botica.com", "ADMIN", 42L);

        assertThat(jwtUtils.validateToken(token)).isTrue();
        assertThat(jwtUtils.getEmailFromToken(token)).isEqualTo("ana@botica.com");
        assertThat(jwtUtils.getRoleFromToken(token)).isEqualTo("ADMIN");
    }

    @Test
    void validateToken_rejectsGarbage() {
        assertThat(jwtUtils.validateToken("no-es-un-jwt")).isFalse();
    }

    @Test
    void validateToken_rejectsTokenSignedWithAnotherSecret() {
        JwtUtils other = new JwtUtils();
        ReflectionTestUtils.setField(other, "jwtSecret", "otro-secreto-distinto-pero-largo-de-al-menos-256-bits-0987654321");
        ReflectionTestUtils.setField(other, "jwtExpiration", 60_000L);
        String foreignToken = other.generateToken("ana@botica.com", "USER", 1L);

        assertThat(jwtUtils.validateToken(foreignToken)).isFalse();
    }

    @Test
    void validateToken_rejectsExpiredToken() {
        ReflectionTestUtils.setField(jwtUtils, "jwtExpiration", -1_000L);
        String expired = jwtUtils.generateToken("ana@botica.com", "USER", 1L);

        assertThat(jwtUtils.validateToken(expired)).isFalse();
    }
}
