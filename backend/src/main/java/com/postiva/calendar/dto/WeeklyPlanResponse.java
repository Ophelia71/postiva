package com.postiva.calendar.dto;

import com.postiva.post.dto.GeneratePostResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record WeeklyPlanResponse(
        UUID id,
        String status,
        UUID socialAccountId,
        String platform,
        String accountName,
        LocalDate startDate,
        LocalDate endDate,
        String goal,
        String timezone,
        String strategy,
        String error,
        /** Snapshot of {@link com.postiva.social.insights.PerformanceSummary} taken when the plan was made. */
        Map<String, Object> performance,
        List<Item> items,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record Item(
            UUID id,
            LocalDate plannedDate,
            Instant scheduledTime,
            String postType,
            String topic,
            String reason,
            String status,
            String error,
            UUID scheduledPostId,
            /** The draft, once written. Null while generating or when drafting failed. */
            GeneratePostResponse post,
            /** Every image or video attached to the draft, in order. Empty when the post is text only. */
            List<Media> media
    ) {
    }

    public record Media(UUID id, String url, String mimeType, String fileName) {
    }
}
