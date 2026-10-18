package com.postiva.admin.service;

import com.postiva.user.entity.User;
import com.postiva.user.repository.UserRepository;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminProvisioningServiceTest {
    private static final ValidatorFactory VALIDATION = Validation.buildDefaultValidatorFactory();
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final AdminProvisioningService service = new AdminProvisioningService(users, passwords, VALIDATION.getValidator());

    @AfterAll
    static void closeValidation() { VALIDATION.close(); }

    @Test
    void newAdminHasNormalizedIdentityAndOnlyAnEncodedPassword() {
        when(users.findByEmailIgnoreCase("admin@example.test")).thenReturn(Optional.empty());
        when(passwords.encode("a-unique-password-123")).thenReturn("encoded-password");
        when(users.save(any())).thenAnswer(call -> call.getArgument(0));
        User created = service.provision(" ADMIN@example.test ", "a-unique-password-123", " Internal Admin ");
        assertEquals("admin@example.test", created.getEmail());
        assertEquals("Internal Admin", created.getFullName());
        assertEquals(User.Role.ADMIN, created.getRole());
        assertTrue(created.isActive());
        assertEquals("encoded-password", created.getPasswordHash());
    }

    @Test
    void bootstrapCannotPromoteAnExistingOrdinaryUser() {
        User existing = new User();
        when(users.findByEmailIgnoreCase("admin@example.test")).thenReturn(Optional.of(existing));
        assertThrows(IllegalStateException.class,
                () -> service.provision("admin@example.test", "a-unique-password-123", "Admin"));
        assertEquals(User.Role.USER, existing.getRole());
        verify(users, never()).save(any());
        verifyNoInteractions(passwords);
    }

    @Test
    void repeatedBootstrapDoesNotResetExistingAdminCredentialsOrStatus() {
        User existing = new User();
        existing.setRole(User.Role.ADMIN);
        existing.setPasswordHash("existing-hash");
        existing.lock(java.util.UUID.randomUUID(), "Existing lock");
        when(users.findByEmailIgnoreCase("admin@example.test")).thenReturn(Optional.of(existing));
        assertSame(existing, service.provision("admin@example.test", "a-different-password", "New name"));
        assertEquals("existing-hash", existing.getPasswordHash());
        assertEquals(User.Status.LOCKED, existing.getStatus());
        verify(users, never()).save(any());
        verifyNoInteractions(passwords);
    }

    @Test
    void invalidOrMissingConfigurationCannotCreateAnAdmin() {
        assertThrows(IllegalStateException.class, () -> service.provision("bad-email", "long-enough-password", "Admin"));
        assertThrows(IllegalStateException.class, () -> service.provision("admin@example.test", "short", "Admin"));
        assertThrows(IllegalStateException.class, () -> service.provision("admin@example.test", "đ".repeat(40), "Admin"));
        assertThrows(IllegalStateException.class, () -> service.provision(null, null, null));
        verifyNoInteractions(users, passwords);
    }
}
