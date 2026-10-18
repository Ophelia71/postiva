package com.postiva.admin.service;

import com.postiva.user.entity.User;
import com.postiva.user.repository.UserRepository;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Internal provisioning only; deliberately has no HTTP endpoint. */
@Service
public class AdminProvisioningService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final Validator validator;

    public AdminProvisioningService(UserRepository users, PasswordEncoder passwords, Validator validator) {
        this.users = users;
        this.passwords = passwords;
        this.validator = validator;
    }

    @Transactional
    public User provision(String email, String password, String fullName) {
        String normalizedEmail = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        String normalizedName = fullName == null ? null : fullName.trim();
        if (!validator.validate(new Identity(normalizedEmail, normalizedName)).isEmpty()
                || password == null || password.isBlank() || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("Cấu hình admin bootstrap không hợp lệ: email/tên hợp lệ, "
                    + "mật khẩu từ 12 ký tự và tối đa 72 byte UTF-8");
        }
        User existing = users.findByEmailIgnoreCase(normalizedEmail).orElse(null);
        if (existing != null) {
            if (existing.getRole() != User.Role.ADMIN) {
                throw new IllegalStateException("Email bootstrap đã thuộc tài khoản USER; không tự nâng quyền");
            }
            // Never rotate a password, unlock, or otherwise overwrite an existing admin.
            return existing;
        }
        User admin = new User();
        admin.setEmail(normalizedEmail);
        admin.setFullName(normalizedName);
        admin.setRole(User.Role.ADMIN);
        admin.setPasswordHash(passwords.encode(password));
        return users.save(admin);
    }

    private static final class Identity {
        @NotBlank @Email @Size(max = 255)
        private final String email;
        @NotBlank @Size(max = 150)
        private final String fullName;

        private Identity(String email, String fullName) {
            this.email = email;
            this.fullName = fullName;
        }
    }
}
