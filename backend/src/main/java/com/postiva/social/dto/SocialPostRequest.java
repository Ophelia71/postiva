package com.postiva.social.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SocialPostRequest(
        UUID postId,
        String title,
        @NotEmpty List<@Valid SocialPostTargetRequest> targets,
        @NotEmpty Map<String, Object> platformContents,
        List<String> hashtags,
        String cta,
        List<UUID> mediaIds,
        OffsetDateTime scheduledTime
) {
}
