package com.postiva.automation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Periodic trigger for the daily automation. It only exists when {@code postiva.automation.enabled}
 * is true. All safety lives in {@link AutomationService#planOnce} and the database constraints, so
 * running this on several instances, or restarting it mid-pass, never creates a second post.
 */
@Component
@ConditionalOnProperty(name = "postiva.automation.enabled", havingValue = "true")
public class AutomationPlanner {
    private static final Logger log = LoggerFactory.getLogger(AutomationPlanner.class);
    private final AutomationService service;

    public AutomationPlanner(AutomationService service) { this.service = service; }

    @Scheduled(fixedDelayString = "${postiva.automation.planner-delay-ms:300000}",
            initialDelayString = "${postiva.automation.planner-delay-ms:300000}")
    public void run() {
        try {
            service.planOnce(Instant.now());
        } catch (RuntimeException e) {
            log.error("Daily automation planner pass failed", e);
        }
    }
}
