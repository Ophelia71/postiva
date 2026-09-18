package com.postiva.social.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Request shape used by the existing Create Post frontend screen. */
public record FacebookBusinessPostRequest(
        @NotBlank String pageId,
        @NotBlank String message,
        String mediaType,
        String mediaUrl,
        UUID mediaId,
        List<UUID> mediaIds,
        OffsetDateTime scheduledTime,
        UUID postId
) {
}
