package com.postiva.scheduler.dto;

import jakarta.validation.constraints.Size;

public record ResolvePublishRequest(
        Result result,
        @Size(max = 255) String externalPostId
) {
    public enum Result {
        CONFIRMED_PUBLISHED,
        CONFIRMED_NOT_PUBLISHED
    }
}
