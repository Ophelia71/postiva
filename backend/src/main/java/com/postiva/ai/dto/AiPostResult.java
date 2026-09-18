package com.postiva.ai.dto;

import java.util.List;
import java.util.Map;

public record AiPostResult(
        String title,
        Map<String, Object> platformContents,
        List<String> hashtags,
        String cta,
        String imageSuggestion
) {
}
