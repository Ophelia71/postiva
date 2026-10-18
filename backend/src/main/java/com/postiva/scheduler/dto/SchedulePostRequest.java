package com.postiva.scheduler.dto;

import com.postiva.common.Platform;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record SchedulePostRequest(
        @NotNull UUID postId,
        @NotNull UUID socialAccountId,
        @NotNull Platform platform,
        @NotNull Instant scheduledTime
) {
}
