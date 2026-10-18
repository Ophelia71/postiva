package com.postiva.scheduler.dto;

import com.postiva.common.Platform;
import com.postiva.scheduler.ScheduledPostStatus;

import java.time.Instant;
import java.util.UUID;

public record ScheduledPostResponse(
        UUID id,
        UUID postId,
        UUID socialAccountId,
        Platform platform,
        Instant scheduledTime,
        ScheduledPostStatus status,
        int attemptCount,
        Instant nextAttemptAt,
        String lastError,
        Instant publishedAt
) {
}
