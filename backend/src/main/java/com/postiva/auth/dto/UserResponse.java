package com.postiva.auth.dto;

import com.postiva.user.entity.User;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String fullName,
        String email,
        String role,
        LocalDateTime createdAt,
        String status,
        boolean mfaSetupRequired
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getRole().name(),
                user.getCreatedAt(),
                user.getStatus().name(),
                user.getRole() == User.Role.ADMIN && !user.isMfaEnabled()
        );
    }
}
