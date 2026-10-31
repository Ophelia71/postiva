package com.postiva.social.dto;

import jakarta.validation.constraints.NotBlank;

public record ManualFacebookConnectRequest(
        @NotBlank String pageId,
        /** Optional: the name comes from the platform; this is only a fallback if the platform returns none. */
        String pageName,
        @NotBlank String pageAccessToken,
        Long expiresInSeconds
) {
}
