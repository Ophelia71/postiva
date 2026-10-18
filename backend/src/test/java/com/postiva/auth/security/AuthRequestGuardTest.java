package com.postiva.auth.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthRequestGuardTest {
    @Test void loginIsThrottledPerAccountAndPerClientIp() {
        var limiter = mock(PersistentRateLimiter.class);
        var guard = new AuthRequestGuard(limiter, "", 10);
        guard.login("person@example.test");
        verify(limiter).consume("login-account", "person@example.test", 10, 900);

        var request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setServletPath("/api/auth/login");
        guard.checkIp(request);
        verify(limiter).consume("login-ip", "127.0.0.1", 60, 900);
    }

    @Test void passwordRecoveryEndpointsAreRateLimitedByClientIp() {
        var limiter = mock(PersistentRateLimiter.class);
        var guard = new AuthRequestGuard(limiter, "", 10);
        var request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setServletPath("/api/auth/password/forgot");

        guard.checkIp(request);

        verify(limiter).consume("password-reset-ip", "127.0.0.1", 30, 3600);
        request.setServletPath("/api/auth/password/reset");
        guard.checkIp(request);
        verify(limiter).consume("password-reset-submit-ip", "127.0.0.1", 60, 900);
    }

    @Test void spoofedHeadersAreIgnoredUnlessPeerIsTrusted() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.20");
        request.addHeader("X-Real-IP", "198.51.100.4");
        request.addHeader("X-Forwarded-For", "203.0.113.5");
        var limiter = mock(PersistentRateLimiter.class);
        assertEquals("192.0.2.20", new AuthRequestGuard(limiter, "", 10).clientAddress(request));
        assertEquals("198.51.100.4", new AuthRequestGuard(limiter, "192.0.2.0/24", 10).clientAddress(request));
        request.removeHeader("X-Real-IP"); request.addHeader("X-Real-IP", "attacker.example");
        assertEquals("192.0.2.20", new AuthRequestGuard(limiter, "192.0.2.0/24", 10).clientAddress(request));
    }
}
