package com.postiva.admin.controller;

import com.postiva.admin.dto.AiRuntimeSwitchRequest;
import com.postiva.auth.service.CurrentUserService;
import com.postiva.common.ApiResponse;
import com.postiva.usage.AiCostService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/admin/ai-budget")
@PreAuthorize("hasRole('ADMIN') and hasAuthority('MFA_VERIFIED')")
public class AdminAiBudgetController {
    private final AiCostService costs;
    private final CurrentUserService currentUser;

    public AdminAiBudgetController(AiCostService costs, CurrentUserService currentUser) {
        this.costs = costs;
        this.currentUser = currentUser;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<AiCostService.BudgetStatus>> status() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(costs.status(Instant.now())));
    }

    @PatchMapping("/switch")
    public ResponseEntity<ApiResponse<AiCostService.BudgetStatus>> switchAi(@Valid @RequestBody AiRuntimeSwitchRequest request) {
        costs.setEnabled(request.enabled(), currentUser.requireCurrentUserId(), request.reason());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(costs.status(Instant.now())));
    }
}
