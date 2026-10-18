package com.postiva.admin.service;

import com.postiva.admin.repository.AdminAuditLogRepository;
import com.postiva.auth.service.CurrentUserService;
import com.postiva.user.entity.User;
import com.postiva.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminUserServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final AdminAuditLogRepository audit = mock(AdminAuditLogRepository.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final AdminUserService service = new AdminUserService(users, audit, currentUser);

    @Test
    void ordinaryUserCannotReadAdminDataWithoutASecurityProxy() {
        when(currentUser.requireCurrentUser()).thenReturn(new User());
        assertThrows(ResponseStatusException.class, () -> service.list("", null, 0, 20));
        assertThrows(ResponseStatusException.class, () -> service.detail(UUID.randomUUID()));
        assertThrows(ResponseStatusException.class, () -> service.audit(UUID.randomUUID(), 0, 20));
        verifyNoInteractions(users, audit);
    }

    @Test
    void lockedAdminCannotReadAdminDataWithoutASecurityProxy() {
        User admin = new User();
        admin.setRole(User.Role.ADMIN);
        admin.lock(UUID.randomUUID(), "Locked fixture");
        when(currentUser.requireCurrentUser()).thenReturn(admin);
        assertThrows(ResponseStatusException.class, () -> service.list("", null, 0, 20));
        verifyNoInteractions(users, audit);
    }
}
