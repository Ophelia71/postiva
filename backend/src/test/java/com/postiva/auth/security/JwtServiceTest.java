package com.postiva.auth.security;

import com.postiva.user.entity.User;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

        UserDetails userDetails =
                org.springframework.security.core.userdetails.User
                        .withUsername("seller@example.com")
                        .password("password")
                        .authorities("ROLE_USER")
                        .build();

        assertEquals("seller@example.com", jwtService.extractEmail(token));
        assertEquals(userId, jwtService.extractUserId(token));
        assertTrue(jwtService.isTokenValid(token, userDetails));
    }

    @Test
    void rejectsTamperedToken() throws Exception {
        User user = createUser(UUID.randomUUID(), "seller@example.com");
        String token = jwtService.generateToken(user);
        String tampered = token.substring(0, token.length() - 1) + "x";

        assertThrows(JwtException.class, () -> jwtService.extractEmail(tampered));
    }

    private User createUser(UUID id, String email) throws Exception {
        User user = new User();
        user.setEmail(email);
        user.setRole(User.Role.USER);
        setId(user, id);
        return user;
    }

    private void setId(User user, UUID id) throws Exception {
        Field idField = User.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(user, id);
    }
}
