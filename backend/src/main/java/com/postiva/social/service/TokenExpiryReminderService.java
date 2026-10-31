package com.postiva.social.service;

import com.postiva.common.Platform;
import com.postiva.mail.EmailService;
import com.postiva.social.client.InstagramGraphClient;
import com.postiva.social.entity.SocialAccount;
import com.postiva.social.repository.SocialAccountRepository;
import com.postiva.user.entity.User;
import com.postiva.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Emails the shop when a channel's access token is about to expire, once at 7, 3 and 1 days left.
 * Postiva only knows the expiry when the shop gave it while connecting. Threads and Instagram-login tokens are
 * renewed by Postiva itself shortly before they expire, so they are left out.
 */
@Service
public class TokenExpiryReminderService {
    static final int WARN_DAYS = 7;
    static final String REMINDED_FOR = "expiryReminderFor";
    static final String REMINDED_LEVEL = "expiryReminderLevel";
    private static final Logger log = LoggerFactory.getLogger(TokenExpiryReminderService.class);

    private final SocialAccountRepository accounts;
    private final UserRepository users;
    private final EmailService email;
    private final String frontendOrigin;

    public TokenExpiryReminderService(SocialAccountRepository accounts, UserRepository users, EmailService email,
                                      @Value("${postiva.frontend-origin:}") String frontendOrigin) {
        this.accounts = accounts;
        this.users = users;
        this.email = email;
        this.frontendOrigin = frontendOrigin == null ? "" : frontendOrigin.replaceAll("/+$", "");
    }

    @Scheduled(fixedDelay = 6 * 3_600_000L, initialDelay = 300_000L)
    public void remindScheduled() {
        try {
            int sent = remindDue(LocalDateTime.now());
            if (sent > 0) {
                log.info("Sent {} token expiry reminders", sent);
            }
        } catch (RuntimeException exception) {
            log.warn("Token expiry reminders failed: {}", exception.getClass().getSimpleName());
        }
    }

    /** Sends the reminders that are due at {@code now} and returns how many emails went out. */
    @Transactional
    public int remindDue(LocalDateTime now) {
        int sent = 0;
        for (SocialAccount account : accounts.findByDisconnectedAtIsNullAndTokenExpiresAtBetween(now, now.plusDays(WARN_DAYS))) {
            if (renewsItself(account)) {
                continue;
            }
            int level = levelFor(now, account.getTokenExpiresAt());
            Map<String, Object> metadata = account.getAccountMetadata() == null
                    ? new HashMap<>() : new HashMap<>(account.getAccountMetadata());
            if (level >= lastLevel(metadata, account.getTokenExpiresAt())) {
                continue;
            }
            Optional<User> owner = users.findById(account.getUserId());
            if (owner.isEmpty()) {
                continue;
            }
            try {
                email.sendTokenExpiring(owner.get().getEmail(), owner.get().getFullName(), label(account),
                        daysLeft(now, account.getTokenExpiresAt()),
                        frontendOrigin.isEmpty() ? null : frontendOrigin + "/app/connections");
            } catch (RuntimeException exception) {
                // Not recorded as sent, so the next run tries again.
                log.warn("Could not send token expiry reminder: {}", exception.getClass().getSimpleName());
                continue;
            }
            metadata.put(REMINDED_FOR, account.getTokenExpiresAt().toString());
            metadata.put(REMINDED_LEVEL, level);
            account.setAccountMetadata(metadata);
            accounts.save(account);
            sent++;
        }
        return sent;
    }

    /** The reminder step for the time left: 1, 3 or 7 days. */
    static int levelFor(LocalDateTime now, LocalDateTime expiresAt) {
        long days = daysLeft(now, expiresAt);
        return days <= 1 ? 1 : days <= 3 ? 3 : WARN_DAYS;
    }

    static long daysLeft(LocalDateTime now, LocalDateTime expiresAt) {
        long hours = Math.max(0, Duration.between(now, expiresAt).toHours());
        return Math.max(1, (hours + 23) / 24);
    }

    /** The step already sent for this exact expiry; a new expiry (reconnected token) starts again. */
    private static int lastLevel(Map<String, Object> metadata, LocalDateTime expiresAt) {
        if (!expiresAt.toString().equals(metadata.get(REMINDED_FOR))) {
            return Integer.MAX_VALUE;
        }
        return metadata.get(REMINDED_LEVEL) instanceof Number level ? level.intValue() : Integer.MAX_VALUE;
    }

    private static boolean renewsItself(SocialAccount account) {
        if (account.getProvider() == Platform.THREADS) {
            return true;
        }
        Map<String, Object> metadata = account.getAccountMetadata();
        return account.getProvider() == Platform.INSTAGRAM && metadata != null
                && InstagramGraphClient.ApiMode.INSTAGRAM_LOGIN.name().equalsIgnoreCase(
                        String.valueOf(metadata.getOrDefault("apiMode", "")));
    }

    private static String label(SocialAccount account) {
        String platform = switch (account.getProvider()) {
            case FACEBOOK -> "Facebook";
            case INSTAGRAM -> "Instagram";
            case THREADS -> "Threads";
        };
        String name = account.getPageName();
        return name == null || name.isBlank() ? platform : platform + " " + name;
    }
}
