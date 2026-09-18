package com.postiva.post.dto;

import java.util.List;
import java.util.Map;

public record UpdatePostRequest(
        String title,
        Map<String, Object> platformContents,
        List<String> hashtags,
        String cta
) {
}
