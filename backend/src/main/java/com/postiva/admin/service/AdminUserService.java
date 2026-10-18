package com.postiva.admin.service;

import com.postiva.admin.dto.AdminAuditResponse;
import com.postiva.admin.dto.AdminPageResponse;
import com.postiva.admin.dto.AdminUserResponse;
import com.postiva.admin.entity.AdminAuditLog;
import com.postiva.admin.repository.AdminAuditLogRepository;
import com.postiva.auth.service.CurrentUserService;
import com.postiva.user.entity.User;
import com.postiva.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasRole('ADMIN') and hasAuthority('MFA_VERIFIED')")
public class AdminUserService {
    private final UserRepository users;
    private final AdminAuditLogRepository audit;
    private final CurrentUserService currentUser;

    public AdminUserService(UserRepository users, AdminAuditLogRepository audit, CurrentUserService currentUser) {
        this.users = users;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    public AdminPageResponse<AdminUserResponse> list(String query, User.Status status, int page, int size) {
        requireAdmin();
        String term = query == null ? "" : query.trim();
        if (term.length() > 255) {
            throw new IllegalArgumentException("Từ khóa không được vượt quá 255 ký tự");
        }
        Specification<User> filter = (root, criteria, builder) -> builder.equal(root.get("role"), User.Role.USER);
        if (status != null) {
            filter = filter.and((root, criteria, builder) -> builder.equal(root.get("status"), status));
        }
        if (!term.isEmpty()) {
            String pattern = "%" + term.toLowerCase(Locale.ROOT).replace("!", "!!")
                    .replace("%", "!%").replace("_", "!_") + "%";
            filter = filter.and((root, criteria, builder) -> builder.or(
                    builder.like(builder.lower(root.get("fullName")), pattern, '!'),
                    builder.like(builder.lower(root.get("email")), pattern, '!')));
        }
        Page<User> result = users.findAll(filter, pageRequest(page, size));
        return AdminPageResponse.from(result.map(AdminUserResponse::from));
    }

    public AdminUserResponse detail(UUID userId) {
        requireAdmin();
        return AdminUserResponse.from(requireTarget(userId));
    }

    public AdminPageResponse<AdminAuditResponse> audit(UUID userId, int page, int size) {
        requireAdmin();
        requireTarget(userId);
        Page<AdminAuditLog> entries = audit.findByTargetUserIdOrderByCreatedAtDesc(userId, pageRequest(page, size));
        Map<UUID, User> actors = users.findAllById(entries.stream().map(AdminAuditLog::getActorUserId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return AdminPageResponse.from(entries.map(entry -> AdminAuditResponse.from(entry,
                actors.containsKey(entry.getActorUserId()) ? actors.get(entry.getActorUserId()).getFullName() : null)));
    }

    private User requireTarget(UUID id) {
        return users.findByIdAndRole(id, User.Role.USER)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"));
    }

    private void requireAdmin() {
        User actor = currentUser.requireCurrentUser();
        if (!actor.isActive() || actor.getRole() != User.Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền quản trị");
        }
    }

    private PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Trang phải từ 0; kích thước trang từ 1 đến 100");
        }
        return PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }
}
