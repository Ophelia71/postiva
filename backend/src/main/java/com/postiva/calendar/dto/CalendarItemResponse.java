package com.postiva.calendar.dto;

import java.time.LocalDate;

public record CalendarItemResponse(
        LocalDate plannedDate,
        String platform,
        String postType,
        String topic,
        String status
) {
}

