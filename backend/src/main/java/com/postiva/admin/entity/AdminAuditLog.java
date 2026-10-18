package com.postiva.admin.entity;

import com.postiva.user.entity.User;
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
@Table(name = "admin_audit_logs")
public class AdminAuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private UUID actorUserId;

    @Column(name = "target_user_id", nullable = false, updatable = false)
    private UUID targetUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false)
    private Action action;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, length = 20, updatable = false)
    private User.Status previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 20, updatable = false)
    private User.Status newStatus;

    @Column(nullable = false, columnDefinition = "TEXT", updatable = false)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AdminAuditLog() {
    }

    public AdminAuditLog(UUID actorUserId, UUID targetUserId, User.Status previousStatus,
                         User.Status newStatus, String reason) {
        this.actorUserId = actorUserId;
        this.targetUserId = targetUserId;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.action = newStatus == User.Status.LOCKED ? Action.LOCK_USER : Action.UNLOCK_USER;
        this.reason = reason;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getActorUserId() { return actorUserId; }
    public UUID getTargetUserId() { return targetUserId; }
    public Action getAction() { return action; }
    public User.Status getPreviousStatus() { return previousStatus; }
    public User.Status getNewStatus() { return newStatus; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }

    public enum Action {
        LOCK_USER,
        UNLOCK_USER
    }
}
