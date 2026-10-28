package com.postiva.calendar.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "content_calendars")
public class ContentCalendar {
    @Id
    @GeneratedValue
    private UUID id;

    private UUID userId;
    private UUID businessId;
    private UUID socialAccountId;
    private String industryCode;
    private String goal;
    private LocalDate startDate;
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContentCalendarStatus status = ContentCalendarStatus.READY;

    /** The product brief the week was planned from (weekly plans only). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> brief = new HashMap<>();

    /** Snapshot of the past-post analysis the plan was based on, so the reasoning stays visible later. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> performance = new HashMap<>();

    @Column(columnDefinition = "TEXT")
    private String strategy;

    private String timezone;

    @Column(columnDefinition = "TEXT")
    private String error;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getIndustryCode() {
        return industryCode;
    }

    public void setIndustryCode(String industryCode) {
        this.industryCode = industryCode;
    }

    public String getGoal() {
        return goal;
    }

    public void setGoal(String goal) {
        this.goal = goal;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public void setBusinessId(UUID businessId) {
        this.businessId = businessId;
    }

    public UUID getSocialAccountId() {
        return socialAccountId;
    }

    public void setSocialAccountId(UUID socialAccountId) {
        this.socialAccountId = socialAccountId;
    }

    public ContentCalendarStatus getStatus() {
        return status;
    }

    public void setStatus(ContentCalendarStatus status) {
        this.status = status;
    }

    public Map<String, Object> getBrief() {
        return brief;
    }

    public void setBrief(Map<String, Object> brief) {
        this.brief = brief;
    }

    public Map<String, Object> getPerformance() {
        return performance;
    }

    public void setPerformance(Map<String, Object> performance) {
        this.performance = performance;
    }

    public String getStrategy() {
        return strategy;
    }

    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
