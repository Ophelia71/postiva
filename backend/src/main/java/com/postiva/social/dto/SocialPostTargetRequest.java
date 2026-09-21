package com.postiva.social.dto;

import com.postiva.common.Platform;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SocialPostTargetRequest(
        @NotNull UUID socialAccountId,
        @NotNull Platform platform
) {
}
