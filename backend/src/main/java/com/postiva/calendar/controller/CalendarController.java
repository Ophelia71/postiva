package com.postiva.calendar.controller;

import com.postiva.calendar.dto.CalendarResponse;
import com.postiva.calendar.dto.GenerateCalendarRequest;
import com.postiva.calendar.service.CalendarService;
import com.postiva.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/calendars")
public class CalendarController {
    private final CalendarService calendarService;

    public CalendarController(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @PostMapping("/generate")
    public ApiResponse<CalendarResponse> generate(@Valid @RequestBody GenerateCalendarRequest request) {
        return ApiResponse.ok(calendarService.generate(request));
    }

    @GetMapping("/{calendarId}")
    public ApiResponse<CalendarResponse> getCalendar(@PathVariable UUID calendarId) {
        return ApiResponse.ok(calendarService.getCalendar(calendarId));
    }
}
