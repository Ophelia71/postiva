package com.postiva.scheduler.dto;

import com.postiva.common.Platform;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PublishNowRequest(
        @NotNull UUID postId,
        @NotNull UUID socialAccountId,
        @NotNull Platform platform
) {
}
