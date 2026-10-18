package com.postiva.auth.security;

import com.postiva.user.entity.User;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {
    private static final String SECRET =
            "a-test-secret-that-is-long-enough-32-bytes";

    private final JwtService jwtService = new JwtService(
            SECRET,
            60_000
    );

    @Test
    void createsAndValidatesToken() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = createUser(userId, "seller@example.com");

        String token = jwtService.generateToken(user);

        UserDetails userDetails = new PostivaUserDetails(user);

        assertEquals("seller@example.com", jwtService.extractEmail(token));
        assertEquals(userId, jwtService.extractUserId(token));
        assertTrue(jwtService.isTokenValid(token, userDetails));
        user.incrementAuthVersion();
        assertFalse(jwtService.isTokenValid(token, new PostivaUserDetails(user)));
    }

    @Test
    void rejectsTamperedToken() throws Exception {
        User user = createUser(UUID.randomUUID(), "seller@example.com");
        String token = jwtService.generateToken(user);
        // The final base64url character can change only unused padding bits and
        // decode to the same signature; mutate a fully significant character.
        int signatureIndex = token.length() - 2;
        char replacement = token.charAt(signatureIndex) == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, signatureIndex) + replacement + token.substring(signatureIndex + 1);

        assertThrows(JwtException.class, () -> jwtService.extractEmail(tampered));
    }

    @Test
    void acceptsLegacyVersionZeroTokenUntilPasswordChanges() throws Exception {
        User user = createUser(UUID.randomUUID(), "seller@example.com");
        String legacyToken = Jwts.builder().setSubject(user.getEmail()).claim("role", "USER")
                .setIssuedAt(new Date()).setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)),
                        SignatureAlgorithm.HS256).compact();

        assertTrue(jwtService.isTokenValid(legacyToken, new PostivaUserDetails(user)));
        user.incrementAuthVersion();
        assertFalse(jwtService.isTokenValid(legacyToken, new PostivaUserDetails(user)));
    }

    private User createUser(UUID id, String email) throws Exception {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("fixture-hash");
        user.setRole(User.Role.USER);
        setId(user, id);
        return user;
    }

    @Test
    void enrolledAdminRequiresMfaProofInTheSignedToken() throws Exception {
        User user = createUser(UUID.randomUUID(), "admin@example.test");
        user.setRole(User.Role.ADMIN);
        user.setMfaEnabled(true);
        assertFalse(jwtService.isTokenValid(jwtService.generateToken(user), new PostivaUserDetails(user)));
        assertTrue(jwtService.isTokenValid(jwtService.generateToken(user, true), new PostivaUserDetails(user)));
    }

    private void setId(User user, UUID id) throws Exception {
        Field idField = User.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(user, id);
    }
}
