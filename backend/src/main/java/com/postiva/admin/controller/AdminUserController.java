package com.postiva.admin.controller;

import com.postiva.admin.dto.AdminAuditResponse;
import com.postiva.admin.dto.AdminPageResponse;
import com.postiva.admin.dto.AdminUserResponse;
import com.postiva.admin.dto.ChangeUserStatusRequest;
import com.postiva.admin.dto.GrantProRequest;
import com.postiva.admin.service.AdminPlanService;
import com.postiva.admin.service.AdminUserService;
import com.postiva.common.ApiResponse;
import com.postiva.user.entity.User;
import com.postiva.user.service.UserAccountStatusService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN') and hasAuthority('MFA_VERIFIED')")
@Validated
public class AdminUserController {
    private final AdminUserService users;
    private final UserAccountStatusService statuses;
    private final AdminPlanService plans;

    public AdminUserController(AdminUserService users, UserAccountStatusService statuses, AdminPlanService plans) {
        this.users = users;
        this.statuses = statuses;
        this.plans = plans;
    }

    @GetMapping
    public ApiResponse<AdminPageResponse<AdminUserResponse>> list(
            @RequestParam(defaultValue = "") @Size(max = 255) String q,
            @RequestParam(required = false) User.Status status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(users.list(q, status, page, size));
    }

    @GetMapping("/{userId}")
    public ApiResponse<AdminUserResponse> detail(@PathVariable UUID userId) {
        return ApiResponse.ok(users.detail(userId));
    }

    @PatchMapping("/{userId}/lock")
    public ApiResponse<AdminUserResponse> lock(@PathVariable UUID userId,
                                              @Valid @RequestBody ChangeUserStatusRequest request) {
        return ApiResponse.ok(AdminUserResponse.from(statuses.lockUser(userId, request.reason())));
    }

    @PatchMapping("/{userId}/unlock")
    public ApiResponse<AdminUserResponse> unlock(@PathVariable UUID userId,
                                                @Valid @RequestBody ChangeUserStatusRequest request) {
        return ApiResponse.ok(AdminUserResponse.from(statuses.unlockUser(userId, request.reason())));
    }

    @GetMapping("/{userId}/audit")
    public ApiResponse<AdminPageResponse<AdminAuditResponse>> audit(@PathVariable UUID userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(users.audit(userId, page, size));
    }

    @GetMapping("/{userId}/plan")
    public ApiResponse<AdminPlanService.Plan> plan(@PathVariable UUID userId) {
        return ApiResponse.ok(plans.plan(userId));
    }

    @PostMapping("/{userId}/pro-grants")
    public ApiResponse<AdminPlanService.Plan> grantPro(@PathVariable UUID userId,
                                                       @Valid @RequestBody GrantProRequest request) {
        return ApiResponse.ok(plans.grantPro(userId, request.days(), request.reason()));
    }

    @PostMapping("/{userId}/pro-revocations")
    public ApiResponse<AdminPlanService.Plan> revokePro(@PathVariable UUID userId,
                                                        @Valid @RequestBody ChangeUserStatusRequest request) {
        return ApiResponse.ok(plans.revokePro(userId, request.reason()));
    }
}
