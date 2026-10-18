package com.postiva.user.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.USER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "locked_by")
    private UUID lockedBy;

    @Column(name = "lock_reason", columnDefinition = "TEXT")
    private String lockReason;

    @Column(name = "auth_version", nullable = false)
    private int authVersion;

    @Column(name = "mfa_enabled", nullable = false)
    private boolean mfaEnabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void beforeInsert() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;

        if (role == null) {
            role = Role.USER;
        }
    }

    @PreUpdate
    void touchUpdatedAt() {
        updatedAt = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public int getAuthVersion() {
        return authVersion;
    }

    public boolean isMfaEnabled() { return mfaEnabled; }

    public void setMfaEnabled(boolean enabled) { this.mfaEnabled = enabled; }

    public void incrementAuthVersion() {
        authVersion = Math.addExact(authVersion, 1);
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public Status getStatus() {
        return status;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public Instant getLockedAt() {
        return lockedAt;
    }

    public UUID getLockedBy() {
        return lockedBy;
    }

    public String getLockReason() {
        return lockReason;
    }

    public void lock(UUID adminId, String reason) {
        Objects.requireNonNull(adminId, "Người khóa tài khoản không được để trống");
        if (reason == null || reason.isBlank() || reason.trim().length() > 1000) {
            throw new IllegalArgumentException("Lý do phải có từ 1 đến 1000 ký tự");
        }
        status = Status.LOCKED;
        lockedAt = Instant.now();
        lockedBy = adminId;
        lockReason = reason.trim();
    }

    public void unlock() {
        status = Status.ACTIVE;
        lockedAt = null;
        lockedBy = null;
        lockReason = null;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public enum Role {
        USER,
        ADMIN
    }

    public enum Status {
        ACTIVE,
        LOCKED
    }
}
