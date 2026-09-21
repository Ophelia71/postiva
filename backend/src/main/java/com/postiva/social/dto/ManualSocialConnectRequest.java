package com.postiva.social.dto;

import jakarta.validation.constraints.NotBlank;

public record ManualSocialConnectRequest(
        String accountId,
        String accountName,
        @NotBlank String accessToken,
        Long expiresInSeconds
) {
}
