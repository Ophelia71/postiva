package com.postiva.analytics;

import com.postiva.auth.service.CurrentUserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/analytics")
public class WebAnalyticsController {
    private final WebAnalyticsService analytics;
    private final CurrentUserService current;

    public WebAnalyticsController(WebAnalyticsService analytics, CurrentUserService current) {
        this.analytics = analytics;
        this.current = current;
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void record(@RequestBody WebAnalyticsService.Event event, Authentication authentication) {
        if (authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()))) {
            if (event != null && event.type() == WebAnalyticsService.Type.EXCLUDE) analytics.excludeAdminSession(event);
            return;
        }
        UUID userId = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_USER".equals(authority.getAuthority()))
                ? current.requireCurrentUserId() : null;
        analytics.record(event, userId);
    }
}
