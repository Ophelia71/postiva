package com.postiva.calendar.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CalendarResponse(
        UUID calendarId,
        String industryCode,
        String goal,
        LocalDate startDate,
        LocalDate endDate,
        List<CalendarItemResponse> items
) {
}

