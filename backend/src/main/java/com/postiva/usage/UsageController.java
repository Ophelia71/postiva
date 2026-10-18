package com.postiva.usage;

import com.postiva.auth.service.CurrentUserService;
import com.postiva.common.ApiResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/usage")
public class UsageController {
    private final AiUsageService usage;
    private final CurrentUserService current;
    public UsageController(AiUsageService usage, CurrentUserService current) { this.usage = usage; this.current = current; }
    @GetMapping
    public ResponseEntity<ApiResponse<AiUsageService.Summary>> summary() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(usage.summary(current.requireCurrentUserId())));
    }
    @GetMapping("/requests")
    public ResponseEntity<ApiResponse<AiUsageService.History>> history(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.ok(usage.history(current.requireCurrentUserId(), page, size)));
    }
}
