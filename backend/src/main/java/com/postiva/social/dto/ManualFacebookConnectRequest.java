package com.postiva.social.dto;

import jakarta.validation.constraints.NotBlank;

public record ManualFacebookConnectRequest(
        @NotBlank String pageId,
        @NotBlank String pageName,
        @NotBlank String pageAccessToken,
        Long expiresInSeconds
) {
}
