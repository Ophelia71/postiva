package com.postiva.scheduler.dto;

import com.postiva.common.Platform;
import com.postiva.scheduler.ScheduledPostStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record ScheduledPostResponse(
        UUID id,
        UUID postId,
        UUID socialAccountId,
        Platform platform,
        LocalDateTime scheduledTime,
        ScheduledPostStatus status,
        int attemptCount,
        String lastError,
        LocalDateTime publishedAt
) {
}
