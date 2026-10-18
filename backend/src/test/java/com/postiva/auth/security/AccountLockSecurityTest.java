package com.postiva.auth.security;

import com.postiva.user.entity.User;
import com.postiva.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountLockSecurityTest {
    private final UserRepository users = mock(UserRepository.class);
    private final CustomUserDetailsService detailsService = new CustomUserDetailsService(users);
    private final JwtService tokens = mock(JwtService.class);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void lockedUserCannotLogInWithCorrectPassword() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        User user = account();
        user.setPasswordHash(encoder.encode("correct-password"));
        user.lock(UUID.randomUUID(), "Tạm khóa");
        when(users.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(detailsService);
        provider.setPasswordEncoder(encoder);

        assertThrows(LockedException.class, () -> provider.authenticate(
                new UsernamePasswordAuthenticationToken(user.getEmail(), "correct-password")));
    }

    @Test
    void previouslyValidJwtStopsAuthenticatingAfterAccountIsLocked() throws Exception {
        User user = account();
        when(users.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(tokens.extractEmail("existing-token")).thenReturn(user.getEmail());
        when(tokens.isTokenValid(eq("existing-token"), any(UserDetails.class))).thenReturn(true);

        filterRequest();
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        SecurityContextHolder.clearContext();

        user.lock(UUID.randomUUID(), "Tạm khóa");
        filterRequest();
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(tokens, times(1)).isTokenValid(eq("existing-token"), any(UserDetails.class));
    }

    @Test
    void deletedUserTokenIsIgnoredInsteadOfCausingServerError() throws Exception {
        CustomUserDetailsService missingUser = mock(CustomUserDetailsService.class);
        when(tokens.extractEmail("existing-token")).thenReturn("missing@example.test");
        when(missingUser.loadUserByUsername("missing@example.test"))
                .thenThrow(new UsernameNotFoundException("Missing"));
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(tokens, missingUser);
        filter.doFilter(request(), new MockHttpServletResponse(), new MockFilterChain());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    private void filterRequest() throws Exception {
        new JwtAuthenticationFilter(tokens, detailsService)
                .doFilter(request(), new MockHttpServletResponse(), new MockFilterChain());
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer existing-token");
        return request;
    }

    private User account() {
        User user = new User();
        user.setEmail("seller@example.test");
        user.setPasswordHash("password-hash");
        return user;
    }
}
