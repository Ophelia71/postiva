package com.postiva.automation;

import com.postiva.mail.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Emails the owner when a draft is waiting for review. Delivery is best effort and never affects the run:
 * failures are logged without the address or provider details, and nothing is sent unless mail is configured.
 */
@Component
public class AutomationNotifier {
    private static final Logger log = LoggerFactory.getLogger(AutomationNotifier.class);

    private final JdbcTemplate jdbc;
    private final EmailService email;
    private final AutomationProperties properties;
    private final String frontendOrigin;

    public AutomationNotifier(JdbcTemplate jdbc, EmailService email, AutomationProperties properties,
                              @Value("${postiva.frontend-origin:}") String frontendOrigin) {
        this.jdbc = jdbc;
        this.email = email;
        this.properties = properties;
        this.frontendOrigin = frontendOrigin;
    }

    @Async("securityEmailExecutor")
    public void draftReady(UUID userId, String ruleName, String plannedAtText) {
        if (!properties.isNotifyEmail()) return;
        try {
            var users = jdbc.queryForList("SELECT email, full_name FROM users WHERE id = ? AND status = 'ACTIVE'", userId);
            if (users.isEmpty()) return;
            String origin = frontendOrigin == null ? "" : frontendOrigin.replaceAll("/+$", "");
            email.sendDailyDraftReady((String) users.getFirst().get("email"), (String) users.getFirst().get("full_name"),
                    ruleName, plannedAtText, origin.isEmpty() ? "" : origin + "/app/daily");
        } catch (RuntimeException e) {
            log.warn("Daily draft notification could not be delivered");
        }
    }
}