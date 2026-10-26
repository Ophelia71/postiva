package com.postiva.automation;

import com.postiva.auth.service.CurrentUserService;
import com.postiva.automation.AutomationDtos.PreviewItem;
import com.postiva.automation.AutomationDtos.RuleRequest;
import com.postiva.automation.AutomationDtos.RuleResponse;
import com.postiva.automation.AutomationDtos.RunResponse;
import com.postiva.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Daily auto-posting API. Every call is scoped to the signed-in USER; other users' rows are 404. */
@RestController
@RequestMapping("/api/automation")
public class AutomationController {
    private final AutomationService service;
    private final CurrentUserService currentUser;

    public AutomationController(AutomationService service, CurrentUserService currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/status")
    public ApiResponse<AutomationService.Status> status() {
        currentUser.requireCurrentUserId();
        return ApiResponse.ok(service.status());
    }

    @GetMapping("/rules")
    public ApiResponse<List<RuleResponse>> rules() {
        return ApiResponse.ok(service.list(currentUser.requireCurrentUserId()));
    }

    @PostMapping("/rules")
    public ApiResponse<RuleResponse> create(@Valid @RequestBody RuleRequest request) {
        return ApiResponse.ok(service.create(currentUser.requireCurrentUserId(), request));
    }

    @GetMapping("/rules/{id}")
    public ApiResponse<RuleResponse> rule(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(currentUser.requireCurrentUserId(), id));
    }

    @PutMapping("/rules/{id}")
    public ApiResponse<RuleResponse> update(@PathVariable UUID id, @Valid @RequestBody RuleRequest request) {
        return ApiResponse.ok(service.update(currentUser.requireCurrentUserId(), id, request));
    }

    @DeleteMapping("/rules/{id}")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        service.delete(currentUser.requireCurrentUserId(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/rules/{id}/pause")
    public ApiResponse<RuleResponse> pause(@PathVariable UUID id) {
        return ApiResponse.ok(service.pause(currentUser.requireCurrentUserId(), id));
    }

    @PostMapping("/rules/{id}/resume")
    public ApiResponse<RuleResponse> resume(@PathVariable UUID id) {
        return ApiResponse.ok(service.resume(currentUser.requireCurrentUserId(), id));
    }

    @GetMapping("/rules/{id}/preview")
    public ApiResponse<List<PreviewItem>> preview(@PathVariable UUID id) {
        return ApiResponse.ok(service.preview(currentUser.requireCurrentUserId(), id));
    }

    @GetMapping("/runs")
    public ApiResponse<List<RunResponse>> runs(@RequestParam(required = false) String status) {
        return ApiResponse.ok(service.runs(currentUser.requireCurrentUserId(), status));
    }

    @PostMapping("/runs/{id}/approve")
    public ApiResponse<RunResponse> approve(@PathVariable UUID id) {
        return ApiResponse.ok(service.approve(currentUser.requireCurrentUserId(), id));
    }

    @PostMapping("/runs/{id}/skip")
    public ApiResponse<RunResponse> skip(@PathVariable UUID id) {
        return ApiResponse.ok(service.skip(currentUser.requireCurrentUserId(), id));
    }
}
