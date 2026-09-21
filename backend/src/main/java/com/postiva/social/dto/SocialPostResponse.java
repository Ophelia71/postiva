package com.postiva.social.dto;

import java.util.List;
import java.util.UUID;

public record SocialPostResponse(
        UUID postId,
        List<SocialPostTargetResponse> targets
) {
}
