package com.postiva.calendar.entity;

public enum CalendarItemStatus {
    /** An idea with no draft yet (also every item of the old template generator). */
    PLANNED,
    GENERATING,
    /** A draft post exists and waits for the shop's approval. */
    DRAFT,
    /** The shop removed this day from the week. */
    SKIPPED,
    SCHEDULED,
    PUBLISHED,
    FAILED
}
