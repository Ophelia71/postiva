package com.postiva.scheduler.dto;

import com.postiva.common.Platform;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

public record SchedulePostRequest(
        @NotNull UUID postId,
        @NotNull UUID socialAccountId,
        @NotNull Platform platform,
        @NotNull LocalDateTime scheduledTime
) {
}
