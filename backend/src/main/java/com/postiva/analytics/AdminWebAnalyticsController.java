package com.postiva.analytics;

import com.postiva.common.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/analytics")
@PreAuthorize("hasRole('ADMIN') and hasAuthority('MFA_VERIFIED')")
public class AdminWebAnalyticsController {
    private final AdminWebAnalyticsService analytics;

    public AdminWebAnalyticsController(AdminWebAnalyticsService analytics) { this.analytics = analytics; }

    @GetMapping
    public ResponseEntity<ApiResponse<AdminWebAnalyticsService.Dashboard>> dashboard(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(analytics.dashboard(days)));
    }
}
