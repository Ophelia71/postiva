package com.postiva.admin.dto;

import com.postiva.user.entity.User;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/** Explicit allowlist: never serialize a User entity from an admin endpoint. */
public record AdminUserResponse(
        UUID id, String fullName, String email, User.Role role, User.Status status,
        LocalDateTime createdAt, LocalDateTime updatedAt,
        Instant lockedAt, UUID lockedBy, String lockReason
) {
    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getRole(),
                user.getStatus(), user.getCreatedAt(), user.getUpdatedAt(),
                user.getLockedAt(), user.getLockedBy(), user.getLockReason());
    }
}
