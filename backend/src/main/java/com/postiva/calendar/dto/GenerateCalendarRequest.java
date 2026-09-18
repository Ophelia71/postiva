package com.postiva.calendar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record GenerateCalendarRequest(
        @NotBlank String industryCode,
        @NotBlank String goal,
        @NotNull LocalDate startDate
) {
}

