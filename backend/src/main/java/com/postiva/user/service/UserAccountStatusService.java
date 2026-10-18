package com.postiva.user.service;

import com.postiva.admin.entity.AdminAuditLog;
import com.postiva.admin.repository.AdminAuditLogRepository;
import com.postiva.auth.service.CurrentUserService;
import com.postiva.scheduler.service.PublishingService;
import com.postiva.user.entity.User;
import com.postiva.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class UserAccountStatusService {
    private final UserRepository userRepository;
    private final AdminAuditLogRepository auditRepository;
    private final CurrentUserService currentUserService;
    private final PublishingService publishingService;

    public UserAccountStatusService(UserRepository userRepository, AdminAuditLogRepository auditRepository,
                                    CurrentUserService currentUserService, PublishingService publishingService) {
        this.userRepository = userRepository;
        this.auditRepository = auditRepository;
        this.currentUserService = currentUserService;
        this.publishingService = publishingService;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('MFA_VERIFIED')")
    public User lockUser(UUID userId, String reason) {
        return changeStatus(userId, User.Status.LOCKED, reason);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('MFA_VERIFIED')")
    public User unlockUser(UUID userId, String reason) {
        return changeStatus(userId, User.Status.ACTIVE, reason);
    }

    private User changeStatus(UUID userId, User.Status nextStatus, String reason) {
        User actor = currentUserService.requireCurrentUser();
        if (!actor.isActive() || actor.getRole() != User.Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền quản trị");
        }
        if (reason == null || reason.isBlank() || reason.trim().length() > 1000) {
            throw new IllegalArgumentException("Lý do phải có từ 1 đến 1000 ký tự");
        }
        User target = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"));
        if (target.getRole() != User.Role.USER || actor.getId().equals(target.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ quản lý trạng thái tài khoản USER");
        }
        if (target.getStatus() == nextStatus) {
            return target;
        }

        User.Status previousStatus = target.getStatus();
        String normalizedReason = reason.trim();
        if (nextStatus == User.Status.LOCKED) {
            target.lock(actor.getId(), normalizedReason);
            publishingService.cancelPendingPostsForUser(target.getId());
        } else {
            target.unlock();
        }
        userRepository.save(target);
        auditRepository.save(new AdminAuditLog(actor.getId(), target.getId(), previousStatus,
                nextStatus, normalizedReason));
        return target;
    }
}
