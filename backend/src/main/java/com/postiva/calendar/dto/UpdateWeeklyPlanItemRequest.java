package com.postiva.calendar.dto;

import java.time.Instant;

/** Changes the shop can make to one day before approving the week. Text edits go through PUT /api/posts/{id}. */
public record UpdateWeeklyPlanItemRequest(
        Instant scheduledTime,
        /** true removes the day from the week, false brings a skipped day back. */
        Boolean skipped
) {
}
