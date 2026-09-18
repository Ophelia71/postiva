package com.postiva.social.dto;

import jakarta.validation.constraints.NotBlank;

public record FacebookTestPostRequest(
        @NotBlank String pageId,
        @NotBlank String message
) {
}
