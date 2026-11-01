package com.postiva.post.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record UpdatePostRequest(
        String title,
        Map<String, Object> platformContents,
        List<String> hashtags,
        String cta,
        /** Replaces the post's images and videos when present; null leaves them as they are. */
        List<UUID> mediaIds
) {
}
