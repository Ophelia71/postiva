package com.postiva.social.dto;

import jakarta.validation.constraints.NotBlank;

public record ManualSocialConnectRequest(
        @NotBlank String accountId,
        /** Optional: the name comes from the platform; this is only a fallback if the platform returns none. */
        String accountName,
        @NotBlank String accessToken,
        Long expiresInSeconds
) {
}
