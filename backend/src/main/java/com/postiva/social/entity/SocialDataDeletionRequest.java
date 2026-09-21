package com.postiva.social.entity;

import com.postiva.common.Platform;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "social_data_deletion_requests")
public class SocialDataDeletionRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, updatable = false)
    private String confirmationCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Platform provider;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private int deletedAccounts;

    @Column(nullable = false, updatable = false)
    private LocalDateTime completedAt;

    public UUID getId() {
        return id;
    }

    public String getConfirmationCode() {
        return confirmationCode;
    }

    public void setConfirmationCode(String confirmationCode) {
        this.confirmationCode = confirmationCode;
    }

    public Platform getProvider() {
        return provider;
    }

    public void setProvider(Platform provider) {
        this.provider = provider;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getDeletedAccounts() {
        return deletedAccounts;
    }

    public void setDeletedAccounts(int deletedAccounts) {
        this.deletedAccounts = deletedAccounts;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
