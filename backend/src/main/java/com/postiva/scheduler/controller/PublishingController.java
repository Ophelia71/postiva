package com.postiva.scheduler.controller;

import com.postiva.common.ApiResponse;
import com.postiva.scheduler.dto.PublishNowRequest;
import com.postiva.scheduler.dto.SchedulePostRequest;
import com.postiva.scheduler.dto.ScheduledPostResponse;
import com.postiva.scheduler.service.PublishingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/publishing")
public class PublishingController {
    private final PublishingService publishingService;

    public PublishingController(PublishingService publishingService) {
        this.publishingService = publishingService;
    }

    @PostMapping("/schedule")
    public ApiResponse<ScheduledPostResponse> schedule(@Valid @RequestBody SchedulePostRequest request) {
        return ApiResponse.ok(publishingService.schedule(request));
    }

    @PostMapping("/now")
    public ApiResponse<ScheduledPostResponse> publishNow(@Valid @RequestBody PublishNowRequest request) {
        return ApiResponse.ok(publishingService.publishNow(request));
    }

    @GetMapping
    public ApiResponse<List<ScheduledPostResponse>> list() {
        return ApiResponse.ok(publishingService.list());
    }

    @PostMapping("/{scheduledPostId}/retry")
    public ApiResponse<ScheduledPostResponse> retry(@PathVariable UUID scheduledPostId) {
        return ApiResponse.ok(publishingService.retry(scheduledPostId));
    }
}
