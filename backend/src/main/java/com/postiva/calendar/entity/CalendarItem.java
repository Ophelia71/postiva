package com.postiva.calendar.entity;

import com.postiva.common.Platform;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "calendar_items")
public class CalendarItem {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private UUID calendarId;

    private UUID generatedPostId;
    private LocalDate plannedDate;

    @Enumerated(EnumType.STRING)
    private Platform platform;

    private String postType;

    @Column(columnDefinition = "TEXT")
    private String topic;

    @Enumerated(EnumType.STRING)
    private CalendarItemStatus status = CalendarItemStatus.PLANNED;

    /** When the post goes out once the week is approved. */
    private Instant scheduledTime;

    /** Why this post fits the shop, in the AI's words, shown to the shop next to the draft. */
    @Column(columnDefinition = "TEXT")
    private String reason;

    private UUID scheduledPostId;

    @Column(columnDefinition = "TEXT")
    private String error;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    public UUID getId() {
        return id;
    }

    public UUID getCalendarId() {
        return calendarId;
    }

    public void setCalendarId(UUID calendarId) {
        this.calendarId = calendarId;
    }

    public UUID getGeneratedPostId() {
        return generatedPostId;
    }

    public void setGeneratedPostId(UUID generatedPostId) {
        this.generatedPostId = generatedPostId;
    }

    public LocalDate getPlannedDate() {
        return plannedDate;
    }

    public void setPlannedDate(LocalDate plannedDate) {
        this.plannedDate = plannedDate;
    }

    public Platform getPlatform() {
        return platform;
    }

    public void setPlatform(Platform platform) {
        this.platform = platform;
    }

    public String getPostType() {
        return postType;
    }

    public void setPostType(String postType) {
        this.postType = postType;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public CalendarItemStatus getStatus() {
        return status;
    }

    public void setStatus(CalendarItemStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public Instant getScheduledTime() {
        return scheduledTime;
    }

    public void setScheduledTime(Instant scheduledTime) {
        this.scheduledTime = scheduledTime;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public UUID getScheduledPostId() {
        return scheduledPostId;
    }

    public void setScheduledPostId(UUID scheduledPostId) {
        this.scheduledPostId = scheduledPostId;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
