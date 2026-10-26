package com.postiva.scheduler.entity;

import com.postiva.common.Platform;
import com.postiva.scheduler.ScheduledPostStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "scheduled_posts")
public class ScheduledPost {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private UUID postId;
    private UUID socialAccountId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Platform platform;
    @Column(nullable = false)
    private Instant scheduledTime;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScheduledPostStatus status = ScheduledPostStatus.SCHEDULED;
    private Integer attemptCount = 0;
    private Instant nextAttemptAt;
    private UUID claimToken;
    private Instant leaseUntil;
    @Column(columnDefinition = "TEXT")
    private String lastError;
    private Instant publishedAt;
    @Column(length = 40)
    private String failureCode;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touchUpdatedAt() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getPostId() { return postId; }
    public void setPostId(UUID postId) { this.postId = postId; }
    public UUID getSocialAccountId() { return socialAccountId; }
    public void setSocialAccountId(UUID socialAccountId) { this.socialAccountId = socialAccountId; }
    public Platform getPlatform() { return platform; }
    public void setPlatform(Platform platform) { this.platform = platform; }
    public Instant getScheduledTime() { return scheduledTime; }
    public void setScheduledTime(Instant scheduledTime) { this.scheduledTime = scheduledTime; }
    public ScheduledPostStatus getStatus() { return status; }
    public void setStatus(ScheduledPostStatus status) { this.status = status; }
    public Integer getAttemptCount() { return attemptCount; }
    public void setAttemptCount(Integer attemptCount) { this.attemptCount = attemptCount; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(Instant nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
    public UUID getClaimToken() { return claimToken; }
    public void setClaimToken(UUID claimToken) { this.claimToken = claimToken; }
    public Instant getLeaseUntil() { return leaseUntil; }
    public void setLeaseUntil(Instant leaseUntil) { this.leaseUntil = leaseUntil; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public String getFailureCode() { return failureCode; }
    public void setFailureCode(String failureCode) { this.failureCode = failureCode; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
}
