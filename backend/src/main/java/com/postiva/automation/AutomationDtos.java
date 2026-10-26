package com.postiva.automation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class AutomationDtos {
    private AutomationDtos() { }

    public record Brief(
            @NotBlank @Size(max = 200) String productName,
            @NotBlank @Size(max = 4000) String productDescription,
            @Size(max = 100) String price,
            @NotBlank @Size(max = 1000) String targetAudience,
            @NotBlank @Size(max = 2000) String highlights,
            @Size(max = 1000) String goal,
            @Size(max = 500) String tone
    ) { }

    public record RuleRequest(
            @NotBlank @Size(max = 120) String name,
            @NotNull UUID socialAccountId,
            @NotBlank @Size(max = 64) String timezone,
            @NotBlank @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Giờ đăng phải có dạng HH:mm") String timeOfDay,
            /** ISO weekdays: 1 = Monday ... 7 = Sunday. */
            @NotEmpty @Size(max = 7) List<@NotNull @Min(1) @Max(7) Integer> daysOfWeek,
            @NotBlank String approvalMode,
            @Min(60) @Max(1440) Integer leadMinutes,
            @NotNull @Valid Brief brief
    ) { }

    public record RuleResponse(
            UUID id,
            UUID socialAccountId,
            String accountName,
            String platform,
            String name,
            String status,
            String pausedReason,
            String timezone,
            String timeOfDay,
            List<Integer> daysOfWeek,
            String approvalMode,
            int leadMinutes,
            Brief brief,
            Instant createdAt,
            Instant updatedAt
    ) { }

    public record PreviewItem(LocalDate runDate, Instant at) { }

    public record RunResponse(
            UUID id,
            UUID ruleId,
            String ruleName,
            LocalDate runDate,
            Instant plannedFor,
            String status,
            UUID postId,
            UUID scheduledPostId,
            String error,
            Draft draft
    ) { }

    /** The generated text the user reviews. Editing goes through the existing {@code PUT /api/posts/{id}}. */
    public record Draft(String title, String content, List<String> hashtags, String cta) { }
}
