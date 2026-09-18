package com.postiva.social.dto;

import java.util.UUID;

public record FacebookBusinessPostResponse(
        UUID postId,
        UUID scheduledPostId,
        String status
) {
}
