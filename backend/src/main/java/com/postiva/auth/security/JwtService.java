package com.postiva.auth.security;

import com.postiva.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

@Service
public class JwtService {

    private final Key signingKey;
    private final long expirationMilliseconds;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-milliseconds:86400000}")
            long expirationMilliseconds
    ) {
        if (secret == null
                || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException(
                    "JWT secret phải có ít nhất 32 byte"
            );
        }

        this.signingKey = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );

        this.expirationMilliseconds = expirationMilliseconds;
    }

    public String generateToken(User user) {
        Date now = new Date();
        Date expiration = new Date(
                now.getTime() + expirationMilliseconds
        );

        return Jwts.builder()
                .setSubject(user.getEmail())
                .claim("userId", user.getId().toString())
                .claim("role", user.getRole().name())
                .setIssuedAt(now)
                .setExpiration(expiration)
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public UUID extractUserId(String token) {
        String userId = extractAllClaims(token)
                .get("userId", String.class);

        return UUID.fromString(userId);
    }

    public boolean isTokenValid(
            String token,
            UserDetails userDetails
    ) {
        String email = extractEmail(token);

        return email != null
                && email.equalsIgnoreCase(userDetails.getUsername())
                && !isTokenExpired(token);
    }

    public long getExpirationSeconds() {
        return expirationMilliseconds / 1000;
    }

    private boolean isTokenExpired(String token) {
        Date expiration = extractClaim(
                token,
                Claims::getExpiration
        );

        return expiration.before(new Date());
    }

    private <T> T extractClaim(
            String token,
            Function<Claims, T> resolver
    ) {
        return resolver.apply(extractAllClaims(token));
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
