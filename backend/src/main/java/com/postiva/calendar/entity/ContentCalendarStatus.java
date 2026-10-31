package com.postiva.calendar.entity;

public enum ContentCalendarStatus {
    /** Past posts are being analysed and drafts written in the background. */
    GENERATING,
    /** Drafts are ready for the shop to review. Also the state of calendars from the old template generator. */
    READY,
    /** The shop approved the week and its posts were handed to the scheduler. */
    APPROVED,
    /** Planning stopped before any draft could be written. */
    FAILED
}
