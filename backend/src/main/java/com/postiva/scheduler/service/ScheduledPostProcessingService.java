package com.postiva.scheduler.service;

import com.postiva.file.entity.UploadedFile;
import com.postiva.file.service.FileService;
import com.postiva.post.entity.GeneratedPost;
import com.postiva.post.repository.GeneratedPostRepository;
import com.postiva.scheduler.ScheduledPostStatus;
import com.postiva.scheduler.entity.ScheduledPost;
import com.postiva.scheduler.repository.ScheduledPostRepository;
import com.postiva.scheduler.service.ScheduledPostStateService.Claim;
import com.postiva.social.client.MetaApiException;
import com.postiva.social.entity.SocialAccount;
import com.postiva.social.publishing.PlatformPublisher;
import com.postiva.social.publishing.PublishPreparationException;
import com.postiva.social.service.SocialAccountService;
import com.postiva.user.entity.User;
import com.postiva.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.SocketException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

@Service
public class ScheduledPostProcessingService {
    private static final Logger log = LoggerFactory.getLogger(ScheduledPostProcessingService.class);
    private static final Pattern ACCESS_TOKEN_QUERY = Pattern.compile(
            "(?i)(access_token(?:%3D|=))[^&\\s]+"
    );
    private static final Pattern ACCESS_TOKEN_FIELD = Pattern.compile(
            "(?i)(access_token\\s*[:=]\\s*\\\"?)[^&\\s\\\"}]+"
    );
    private final ScheduledPostRepository schedules;
    private final GeneratedPostRepository posts;
    private final SocialAccountService accounts;
    private final UserRepository users;
    private final FileService files;
    private final ScheduledPostStateService states;
    private final Map<com.postiva.common.Platform, PlatformPublisher> publishers;
    private final int maxAttempts;
    private final long backoffBaseSeconds;
    private final long backoffMaxSeconds;

    public ScheduledPostProcessingService(ScheduledPostRepository schedules,
                                          GeneratedPostRepository posts,
                                          SocialAccountService accounts,
                                          UserRepository users,
                                          FileService files,
                                          ScheduledPostStateService states,
                                          List<PlatformPublisher> platformPublishers,
                                          @Value("${postiva.scheduler.max-attempts:4}") int maxAttempts,
                                          @Value("${postiva.scheduler.backoff-base-seconds:60}") long backoffBaseSeconds,
                                          @Value("${postiva.scheduler.backoff-max-seconds:3600}") long backoffMaxSeconds) {
        this.schedules = schedules;
        this.posts = posts;
        this.accounts = accounts;
        this.users = users;
        this.files = files;
        this.states = states;
        this.publishers = platformPublishers.stream().collect(Collectors.toUnmodifiableMap(
                PlatformPublisher::platform, Function.identity()));
        this.maxAttempts = Math.max(1, maxAttempts);
        this.backoffBaseSeconds = Math.max(1, backoffBaseSeconds);
        this.backoffMaxSeconds = Math.max(this.backoffBaseSeconds, backoffMaxSeconds);
    }

    public void process(Claim claim) {
        ScheduledPost scheduled = schedules.findByIdAndClaimTokenAndStatus(
                        claim.scheduledPostId(), claim.claimToken(), ScheduledPostStatus.PUBLISHING)
                .orElse(null);
        if (scheduled == null) return;

        GeneratedPost post = posts.findById(scheduled.getPostId()).orElse(null);
        if (post == null) {
            states.retryOrFail(claim, "Bài viết này đã bị xóa nên không đăng được.", false, maxAttempts, null);
            return;
        }
        if (post.getUserId() == null || !users.findById(post.getUserId()).map(User::isActive).orElse(false)) {
            states.cancel(claim, "ACCOUNT_LOCKED");
            return;
        }

        SocialAccount account;
        List<UploadedFile> mediaFiles;
        String accessToken;
        PlatformPublisher publisher = publishers.get(scheduled.getPlatform());
        if (publisher == null) {
            states.retryOrFail(claim, "Postiva chưa hỗ trợ đăng lên " + platformLabel(scheduled) + ".", false, maxAttempts, null);
            return;
        }
        try {
            account = accounts.prepareForPublishing(scheduled.getSocialAccountId());
            validateAccount(account, scheduled);
            accessToken = accounts.decryptToken(account);
            mediaFiles = files.allMediaForPost(post.getId());
        } catch (RuntimeException exception) {
            boolean retryable = isSafeToRetry(exception);
            states.retryOrFail(claim, userMessage(scheduled, exception), retryable, maxAttempts,
                    retryable ? nextAttemptAt(scheduled.getAttemptCount()) : null);
            return;
        }

        String externalPostId;
        try {
            externalPostId = publisher.publish(post, account, accessToken, mediaFiles);
        } catch (PublishPreparationException exception) {
            boolean retryable = isSafeToRetry(exception);
            states.retryOrFail(claim, userMessage(scheduled, exception.getCause()), retryable, maxAttempts,
                    retryable ? nextAttemptAt(scheduled.getAttemptCount()) : null);
            return;
        } catch (RuntimeException exception) {
            handleProviderFailure(claim, scheduled, exception);
            return;
        }

        if (externalPostId == null || externalPostId.isBlank()) {
            states.markUnknown(claim, PublishErrorMessages.outcomeUnknown(platformLabel(scheduled)));
            return;
        }
        // If this DB transaction fails, the lease will expire into UNKNOWN; never publish again blindly.
        states.complete(claim, externalPostId, Instant.now());
    }

    private void handleProviderFailure(Claim claim, ScheduledPost scheduled, RuntimeException exception) {
        Integer status = providerStatus(exception);
        if (status != null && status == 429) {
            states.retryOrFail(claim, userMessage(scheduled, exception), true, maxAttempts,
                    nextAttemptAt(scheduled.getAttemptCount()));
            return;
        }
        if (status != null && status >= 400 && status < 500 && status != 408) {
            states.retryOrFail(claim, userMessage(scheduled, exception), false, maxAttempts, null);
            return;
        }
        log.warn("Publishing {} {} has an unknown outcome: {}", scheduled.getPlatform(), scheduled.getId(),
                truncate(errorMessage(exception)));
        states.markUnknown(claim, PublishErrorMessages.outcomeUnknown(platformLabel(scheduled)));
    }

    private boolean isSafeToRetry(RuntimeException exception) {
        if (containsTokenOrPermissionFailure(exception)) return false;
        Integer status = providerStatus(exception);
        if (status != null) return status == 408 || status == 429 || status >= 500;
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof WebClientRequestException
                    || cause instanceof TimeoutException
                    || cause instanceof SocketException
                    || cause instanceof IOException) {
                return true;
            }
        }
        return false;
    }

    private Integer providerStatus(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof MetaApiException meta) return meta.getStatusCode();
            if (cause instanceof WebClientResponseException response) return response.getStatusCode().value();
            if (cause instanceof ResponseStatusException response) return response.getStatusCode().value();
        }
        return null;
    }

    private boolean containsTokenOrPermissionFailure(Throwable exception) {
        String message = errorMessage(exception).toLowerCase();
        return message.contains("access token") || message.contains("token đã hết hạn") || message.contains("kết nối lại")
                || message.contains("permission") || message.contains("quyền")
                || message.contains("invalid oauth") || message.contains("session has expired");
    }

    private Instant nextAttemptAt(int attemptCount) {
        int exponent = Math.max(0, Math.min(20, attemptCount - 1));
        long multiplier = 1L << exponent;
        long seconds = Math.min(backoffMaxSeconds, backoffBaseSeconds * multiplier);
        return Instant.now().plusSeconds(seconds);
    }

    private void validateAccount(SocialAccount account, ScheduledPost scheduled) {
        if (account.getProvider() != scheduled.getPlatform()) {
            throw new IllegalStateException("Tài khoản kết nối không đúng nền tảng " + scheduled.getPlatform().name());
        }
        if (account.getTokenExpiresAt() != null
                && account.getTokenExpiresAt().isBefore(java.time.LocalDateTime.now())) {
            throw new IllegalStateException(platformLabel(scheduled) + " access token đã hết hạn, hãy kết nối lại tài khoản");
        }
        if (account.getAccessTokenEncrypted() == null || account.getAccessTokenEncrypted().isBlank()) {
            throw new IllegalStateException(platformLabel(scheduled) + " chưa có access token");
        }
    }

    /** What the shop sees; the provider's own wording, minus any token, stays in the server log. */
    private String userMessage(ScheduledPost scheduled, Throwable exception) {
        String raw = errorMessage(exception);
        log.warn("Publishing {} {} failed: {}", scheduled.getPlatform(), scheduled.getId(), truncate(raw));
        return PublishErrorMessages.forUser(platformLabel(scheduled), raw);
    }

    private String errorMessage(Throwable exception) {
        String message = exception instanceof ResponseStatusException response
                ? response.getReason() : exception.getMessage();
        if (message == null || message.isBlank()) return "Đăng bài thất bại";
        return ACCESS_TOKEN_FIELD.matcher(ACCESS_TOKEN_QUERY.matcher(message)
                .replaceAll("$1[REDACTED]")).replaceAll("$1[REDACTED]");
    }

    private String truncate(String message) {
        return message.length() <= 800 ? message : message.substring(0, 800);
    }

    private String platformLabel(ScheduledPost scheduled) {
        return switch (scheduled.getPlatform()) {
            case FACEBOOK -> "Facebook";
            case INSTAGRAM -> "Instagram";
            case THREADS -> "Threads";
        };
    }
}
