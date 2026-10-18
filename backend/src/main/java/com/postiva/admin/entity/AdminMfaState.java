package com.postiva.admin.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "admin_mfa")
public class AdminMfaState {
    @Id @Column(name = "user_id")
    private UUID userId;
    @Column(name = "secret_encrypted", columnDefinition = "TEXT")
    private String secretEncrypted;
    @Column(name = "setup_expires_at")
    private Instant setupExpiresAt;
    @Column(name = "last_used_step", nullable = false)
    private long lastUsedStep = -1;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recovery_hashes", nullable = false, columnDefinition = "jsonb")
    private List<String> recoveryHashes = new ArrayList<>();
    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;
    @Column(name = "blocked_until")
    private Instant blockedUntil;

    protected AdminMfaState() { }
    public AdminMfaState(UUID userId) { this.userId = userId; }
    public UUID getUserId() { return userId; }
    public String getSecretEncrypted() { return secretEncrypted; }
    public void setSecretEncrypted(String value) { secretEncrypted = value; }
    public Instant getSetupExpiresAt() { return setupExpiresAt; }
    public void setSetupExpiresAt(Instant value) { setupExpiresAt = value; }
    public long getLastUsedStep() { return lastUsedStep; }
    public void setLastUsedStep(long value) { lastUsedStep = value; }
    public List<String> getRecoveryHashes() { return recoveryHashes; }
    public void setRecoveryHashes(List<String> value) { recoveryHashes = new ArrayList<>(value); }
    public int getFailedAttempts() { return failedAttempts; }
    public void setFailedAttempts(int value) { failedAttempts = value; }
    public Instant getBlockedUntil() { return blockedUntil; }
    public void setBlockedUntil(Instant value) { blockedUntil = value; }
}
