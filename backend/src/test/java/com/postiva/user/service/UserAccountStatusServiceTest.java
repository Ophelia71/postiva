package com.postiva.user.service;

import com.postiva.admin.entity.AdminAuditLog;
import com.postiva.admin.repository.AdminAuditLogRepository;
import com.postiva.auth.service.CurrentUserService;
import com.postiva.scheduler.service.PublishingService;
import com.postiva.user.entity.User;
import com.postiva.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserAccountStatusServiceTest {
    @Mock private UserRepository users;
    @Mock private AdminAuditLogRepository audit;
    @Mock private CurrentUserService currentUser;
    @Mock private PublishingService publishing;
    private UserAccountStatusService service;
    private User admin;
    private User target;

    @BeforeEach
    void setUp() {
        service = new UserAccountStatusService(users, audit, currentUser, publishing);
        admin = account(User.Role.ADMIN);
        target = account(User.Role.USER);
    }

    @Test
    void lockingRecordsActorReasonTransitionAndCancelsSchedules() {
        adminAndTarget();
        service.lockUser(target.getId(), "  Vi phạm điều khoản  ");

        assertEquals(User.Status.LOCKED, target.getStatus());
        assertEquals(admin.getId(), target.getLockedBy());
        assertEquals("Vi phạm điều khoản", target.getLockReason());
        assertNotNull(target.getLockedAt());
        ArgumentCaptor<AdminAuditLog> entry = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(audit).save(entry.capture());
        assertEquals(admin.getId(), entry.getValue().getActorUserId());
        assertEquals(target.getId(), entry.getValue().getTargetUserId());
        assertEquals(User.Status.ACTIVE, entry.getValue().getPreviousStatus());
        assertEquals(User.Status.LOCKED, entry.getValue().getNewStatus());
        assertEquals(AdminAuditLog.Action.LOCK_USER, entry.getValue().getAction());
        assertEquals("Vi phạm điều khoản", entry.getValue().getReason());
        verify(publishing).cancelPendingPostsForUser(target.getId());
    }

    @Test
    void unlockingClearsLockMetadataAndKeepsAnAuditOfTheTransition() {
        adminAndTarget();
        target.lock(admin.getId(), "Khóa trước đó");
        service.unlockUser(target.getId(), "Đã xử lý");

        assertTrue(target.isActive());
        assertNull(target.getLockedAt());
        assertNull(target.getLockedBy());
        assertNull(target.getLockReason());
        ArgumentCaptor<AdminAuditLog> entry = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(audit).save(entry.capture());
        assertEquals(AdminAuditLog.Action.UNLOCK_USER, entry.getValue().getAction());
        assertEquals(User.Status.LOCKED, entry.getValue().getPreviousStatus());
        assertEquals("Đã xử lý", entry.getValue().getReason());
        verifyNoInteractions(publishing);
    }

    @Test
    void repeatedLockDoesNotReplaceOriginalReasonOrDuplicateAudit() {
        adminAndTarget();
        target.lock(admin.getId(), "Lý do gốc");
        service.lockUser(target.getId(), "Yêu cầu gửi lại");

        assertEquals("Lý do gốc", target.getLockReason());
        verifyNoInteractions(audit, publishing);
        verify(users, never()).save(any());
    }

    @Test
    void ordinaryUserCannotChangeStatusEvenWhenCalledWithoutSecurityProxy() {
        when(currentUser.requireCurrentUser()).thenReturn(target);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.lockUser(UUID.randomUUID(), "Không hợp lệ"));
        assertEquals(403, error.getStatusCode().value());
        verifyNoInteractions(users, audit, publishing);
    }

    @Test
    void adminAccountsCannotBeLockedThroughUserManagement() {
        when(currentUser.requireCurrentUser()).thenReturn(admin);
        when(users.findByIdForUpdate(admin.getId())).thenReturn(Optional.of(admin));
        assertThrows(ResponseStatusException.class, () -> service.lockUser(admin.getId(), "Không hợp lệ"));
        verifyNoInteractions(audit, publishing);
    }

    @Test
    void emptyAndOversizedReasonsAreRejectedBeforeChangingData() {
        when(currentUser.requireCurrentUser()).thenReturn(admin);
        assertThrows(IllegalArgumentException.class, () -> service.lockUser(target.getId(), "  "));
        assertThrows(IllegalArgumentException.class, () -> service.unlockUser(target.getId(), "a".repeat(1001)));
        verifyNoInteractions(users, audit, publishing);
    }

    private void adminAndTarget() {
        when(currentUser.requireCurrentUser()).thenReturn(admin);
        when(users.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));
    }

    private User account(User.Role role) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        user.setRole(role);
        return user;
    }
}
