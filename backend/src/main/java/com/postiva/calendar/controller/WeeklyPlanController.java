package com.postiva.calendar.controller;

import com.postiva.calendar.dto.CreateWeeklyPlanRequest;
import com.postiva.calendar.dto.UpdateWeeklyPlanItemRequest;
import com.postiva.calendar.dto.WeeklyPlanResponse;
import com.postiva.calendar.service.WeeklyPlanService;
import com.postiva.common.ApiResponse;
import com.postiva.social.insights.PerformanceSummary;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/weekly-plans")
public class WeeklyPlanController {
    private final WeeklyPlanService weeklyPlanService;

    public WeeklyPlanController(WeeklyPlanService weeklyPlanService) {
        this.weeklyPlanService = weeklyPlanService;
    }

    @GetMapping
    public ApiResponse<List<WeeklyPlanResponse>> list() {
        return ApiResponse.ok(weeklyPlanService.list());
    }

    /** Starts planning in the background; poll GET /{planId} until status leaves GENERATING. */
    @PostMapping
    public ApiResponse<WeeklyPlanResponse> create(@Valid @RequestBody CreateWeeklyPlanRequest request) {
        return ApiResponse.ok(weeklyPlanService.create(request));
    }

    @GetMapping("/insights")
    public ApiResponse<PerformanceSummary> insights(@RequestParam UUID socialAccountId,
                                                    @RequestParam(required = false) String timezone) {
        return ApiResponse.ok(weeklyPlanService.insights(socialAccountId, timezone));
    }

    @GetMapping("/{planId}")
    public ApiResponse<WeeklyPlanResponse> get(@PathVariable UUID planId) {
        return ApiResponse.ok(weeklyPlanService.get(planId));
    }

    @PatchMapping("/{planId}/items/{itemId}")
    public ApiResponse<WeeklyPlanResponse> updateItem(@PathVariable UUID planId, @PathVariable UUID itemId,
                                                      @RequestBody UpdateWeeklyPlanItemRequest request) {
        return ApiResponse.ok(weeklyPlanService.updateItem(planId, itemId, request));
    }

    @PostMapping("/{planId}/approve")
    public ApiResponse<WeeklyPlanResponse> approve(@PathVariable UUID planId) {
        return ApiResponse.ok(weeklyPlanService.approve(planId));
    }

    @DeleteMapping("/{planId}")
    public ApiResponse<Void> delete(@PathVariable UUID planId) {
        weeklyPlanService.delete(planId);
        return ApiResponse.ok(null);
    }
}
