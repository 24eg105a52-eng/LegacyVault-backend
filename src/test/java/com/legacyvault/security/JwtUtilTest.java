package com.legacyvault.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(
                "LegacyVaultSecretKeyMustBeAtLeast256BitsLongForHMACSHA256SignatureRequirements",
                3600000L // 1 hour
        );
    }

    @Test
    void generateAndValidateToken_ShouldExtractClaimsSuccessfully() {
        String token = jwtUtil.generateToken("owner@example.com", 42L, "ROLE_USER");

        assertNotNull(token);
        assertTrue(jwtUtil.validateToken(token));
        assertEquals("owner@example.com", jwtUtil.extractEmail(token));
        assertEquals(42L, jwtUtil.extractUserId(token));
    }

    @Test
    void validateToken_WhenTampered_ShouldReturnFalse() {
        String token = jwtUtil.generateToken("owner@example.com", 42L, "ROLE_USER");
        String tamperedToken = token + "xyz";

        assertFalse(jwtUtil.validateToken(tamperedToken));
    }
}
