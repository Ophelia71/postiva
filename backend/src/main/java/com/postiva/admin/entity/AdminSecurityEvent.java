package com.postiva.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "admin_security_events")
public class AdminSecurityEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "admin_user_id", nullable = false, updatable = false)
    private UUID adminUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, updatable = false)
    private Action action;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AdminSecurityEvent() {
    }

    public AdminSecurityEvent(UUID adminUserId) {
        this(adminUserId, Action.PASSWORD_CHANGED);
    }

    public AdminSecurityEvent(UUID adminUserId, Action action) {
        this.adminUserId = adminUserId;
        this.action = action;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getAdminUserId() { return adminUserId; }
    public Action getAction() { return action; }
    public Instant getCreatedAt() { return createdAt; }

    public enum Action { PASSWORD_CHANGED, MFA_ENABLED, MFA_DISABLED, MFA_RECOVERY_USED,
        MFA_RECOVERY_REGENERATED, MFA_FAILED }
}
