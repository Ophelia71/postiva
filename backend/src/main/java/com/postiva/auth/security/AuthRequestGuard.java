package com.postiva.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;

@Component
public class AuthRequestGuard {
    private final PersistentRateLimiter limiter;
    private final List<IpAddressMatcher> trustedProxies;
    private final int loginLimit;
    public AuthRequestGuard(PersistentRateLimiter limiter,
                            @Value("${postiva.security.trusted-proxy-addresses:}") String proxies,
                            @Value("${postiva.security.login-attempts-per-15-minutes:10}") int loginLimit) {
        this.limiter = limiter;
        this.loginLimit = loginLimit;
        this.trustedProxies = Arrays.stream(proxies.split(",")).map(String::trim)
                .filter(s -> !s.isEmpty()).map(IpAddressMatcher::new).toList();
        if (loginLimit < 1) throw new IllegalArgumentException("Giới hạn login phải lớn hơn 0");
    }
    public void login(String email) { limiter.consume("login-account", email, loginLimit, 900); }
    public void passwordResetRequest(String email) {
        limiter.consume("password-reset-cooldown", email, 1, 60);
        limiter.consume("password-reset-account", email, 5, 3600);
    }
    public void passwordResetAttempt(String tokenHash) {
        limiter.consume("password-reset-token", tokenHash, 10, 900);
    }
    public void registration(String email) {
        limiter.consume("register-cooldown", email, 1, 60);
        limiter.consume("register-account", email, 5, 3600);
    }
    public void verification(String email) { limiter.consume("verify-account", email, 10, 600); }
    public void checkIp(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) return;
        switch (request.getServletPath()) {
            case "/api/auth/login" -> limiter.consume("login-ip", clientAddress(request), 60, 900);
            case "/api/auth/register" -> limiter.consume("register-ip", clientAddress(request), 20, 3600);
            case "/api/auth/register/verify" -> limiter.consume("verify-ip", clientAddress(request), 60, 600);
            case "/api/auth/password/forgot" -> limiter.consume("password-reset-ip", clientAddress(request), 30, 3600);
            case "/api/auth/password/reset" -> limiter.consume("password-reset-submit-ip", clientAddress(request), 60, 900);
            case "/api/analytics/events" -> limiter.consume("analytics-ip", clientAddress(request), 3000, 3600);
            default -> { }
        }
    }
    String clientAddress(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        // Trust X-Real-IP only from explicitly configured proxies that overwrite it.
        if (trustedProxies.stream().anyMatch(proxy -> proxy.matches(remote))) {
            String header = request.getHeader("X-Real-IP");
            if (header != null && header.length() <= 45 && header.matches("[0-9a-fA-F:.]+")) {
                try { return InetAddress.getByName(header).getHostAddress(); }
                catch (Exception ignored) { /* use the actual peer */ }
            }
        }
        return remote;
    }
}
