package com.postiva.social.dto;

import jakarta.validation.constraints.NotBlank;

public record ManualSocialConnectRequest(
        @NotBlank String accountId,
        @NotBlank String accountName,
        @NotBlank String accessToken,
        Long expiresInSeconds
) {
}
