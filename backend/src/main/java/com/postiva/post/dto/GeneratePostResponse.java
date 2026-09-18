package com.postiva.post.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record GeneratePostResponse(
        UUID postId,
        UUID briefId,
        String title,
        Map<String, Object> platformContents,
        List<String> hashtags,
        String cta,
        String imageSuggestion,
        String imageUrl,
        String status
) {
}
