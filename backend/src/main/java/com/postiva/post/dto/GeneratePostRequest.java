package com.postiva.post.dto;

import com.postiva.common.Platform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record GeneratePostRequest(
        @NotBlank @Size(max = 200) String productName,
        @NotBlank @Size(max = 4000) String productDescription,
        @Size(max = 100) String price,
        @Size(max = 100) String industryCode,
        @NotBlank @Size(max = 1000) String targetAudience,
        @NotBlank @Size(max = 2000) String highlights,
        @NotBlank @Size(max = 1000) String goal,
        @NotBlank @Size(max = 500) String tone,
        @NotEmpty @Size(max = 3) List<@NotNull Platform> platforms,
        @Size(max = 4000) String additionalInfo,
        @Size(max = 20) List<@NotNull UUID> imageIds
) {
}
