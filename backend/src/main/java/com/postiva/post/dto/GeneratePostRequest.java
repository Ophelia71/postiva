package com.postiva.post.dto;

import com.postiva.common.Platform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record GeneratePostRequest(
        @NotBlank String productName,
        @NotBlank String productDescription,
        String price,
        String industryCode,
        @NotBlank String targetAudience,
        @NotBlank String highlights,
        @NotBlank String goal,
        @NotBlank String tone,
        @NotEmpty List<Platform> platforms,
        String additionalInfo,
        List<UUID> imageIds
) {
}
