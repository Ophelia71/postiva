package com.postiva.social.dto;

import com.postiva.common.Platform;
import com.postiva.scheduler.ScheduledPostStatus;

import java.util.UUID;

public record SocialPostTargetResponse(
        UUID socialAccountId,
        Platform platform,
        UUID scheduledPostId,
        ScheduledPostStatus status,
        String errorMessage
) {
}
