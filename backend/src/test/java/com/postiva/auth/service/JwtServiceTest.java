package com.postiva.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JwtServiceTest {
    private final JwtService jwtService = new JwtService(
            new ObjectMapper(), "a-test-secret-that-is-long-enough", 60
    );

    @Test
    void createsAndValidatesToken() {
        String token = jwtService.createToken("seller@example.com");

        assertEquals("seller@example.com", jwtService.validateAndGetSubject(token));
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwtService.createToken("seller@example.com");
        String tampered = token.substring(0, token.length() - 1) + "x";

        assertNull(jwtService.validateAndGetSubject(tampered));
    }
}
