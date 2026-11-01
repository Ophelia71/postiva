package com.postiva.calendar.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Adds one more post to a finished week, on a day that has none. */
public record AddWeeklyPlanItemRequest(@NotNull LocalDate plannedDate) {
}
