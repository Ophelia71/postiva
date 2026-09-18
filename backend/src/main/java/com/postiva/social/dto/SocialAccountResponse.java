package com.postiva.social.dto;

import com.postiva.common.Platform;

import java.time.LocalDateTime;
import java.util.UUID;

public record SocialAccountResponse(
        UUID id,
        Platform provider,
        String pageId,
        String pageName,
        LocalDateTime tokenExpiresAt,
        LocalDateTime connectedAt
) {
}
