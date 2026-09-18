package com.postiva.calendar.entity;

import com.postiva.common.Platform;
import com.postiva.common.PostStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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
    private PostStatus status = PostStatus.PLANNED;

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

    public PostStatus getStatus() {
        return status;
    }

    public void setStatus(PostStatus status) {
        this.status = status;
    }
}
