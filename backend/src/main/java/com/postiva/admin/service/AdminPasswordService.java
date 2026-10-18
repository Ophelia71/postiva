package com.postiva.admin.service;

import com.postiva.admin.entity.AdminSecurityEvent;
import com.postiva.admin.repository.AdminSecurityEventRepository;
import com.postiva.auth.service.CurrentUserService;
import com.postiva.user.entity.User;
import com.postiva.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

@Service
public class AdminPasswordService {
    private final CurrentUserService currentUser;
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final AdminSecurityEventRepository events;

    public AdminPasswordService(CurrentUserService currentUser, UserRepository users,
                                PasswordEncoder passwords, AdminSecurityEventRepository events) {
        this.currentUser = currentUser;
        this.users = users;
        this.passwords = passwords;
        this.events = events;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void changePassword(String currentPassword, String newPassword) {
        User actor = currentUser.requireCurrentUser();
        if (actor.getRole() != User.Role.ADMIN || !actor.isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền quản trị");
        }
        if (newPassword == null || newPassword.isBlank() || newPassword.length() < 12
                || newPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Mật khẩu mới phải từ 12 ký tự và tối đa 72 byte UTF-8");
        }
        User locked = users.findByIdForUpdate(actor.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Tài khoản không tồn tại"));
        if (locked.getRole() != User.Role.ADMIN || !locked.isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bạn không có quyền quản trị");
        }
        if (currentPassword == null || !passwords.matches(currentPassword, locked.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Mật khẩu hiện tại không đúng");
        }
        if (passwords.matches(newPassword, locked.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Mật khẩu mới phải khác mật khẩu hiện tại");
        }
        locked.setPasswordHash(passwords.encode(newPassword));
        locked.incrementAuthVersion();
        users.save(locked);
        events.save(new AdminSecurityEvent(locked.getId()));
    }
}
