package com.postiva.scheduler.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "publish_logs")
public class PublishLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false)
    private UUID scheduledPostId;
    private String provider;
    private String externalPostId;
    @Column(nullable = false)
    private String status;
    @Column(columnDefinition = "TEXT")
    private String errorMessage;
    private Instant publishedAt;

    public UUID getId() { return id; }
    public UUID getScheduledPostId() { return scheduledPostId; }
    public String getProvider() { return provider; }
    public String getExternalPostId() { return externalPostId; }
    public String getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getPublishedAt() { return publishedAt; }

    public void setScheduledPostId(UUID scheduledPostId) { this.scheduledPostId = scheduledPostId; }
    public void setProvider(String provider) { this.provider = provider; }
    public void setExternalPostId(String externalPostId) { this.externalPostId = externalPostId; }
    public void setStatus(String status) { this.status = status; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
}
