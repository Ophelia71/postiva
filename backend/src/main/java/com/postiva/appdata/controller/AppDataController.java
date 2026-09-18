package com.postiva.appdata.controller;

import com.postiva.auth.service.CurrentUserService;
import com.postiva.common.ApiResponse;
import com.postiva.common.PostStatus;
import com.postiva.social.dto.SocialAccountResponse;
import com.postiva.social.service.SocialAccountService;
import com.postiva.post.entity.GeneratedPost;
import com.postiva.post.repository.GeneratedPostRepository;
import com.postiva.scheduler.entity.ScheduledPost;
import com.postiva.scheduler.repository.ScheduledPostRepository;
import com.postiva.scheduler.repository.PublishLogRepository;
import com.postiva.scheduler.entity.PublishLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.HashMap;
import java.util.HashSet;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/app-data")
public class AppDataController {
    private static final Logger log = LoggerFactory.getLogger(AppDataController.class);

    private final CurrentUserService currentUserService;
    private final SocialAccountService socialAccountService;
    private final GeneratedPostRepository postRepository;
    private final ScheduledPostRepository scheduledRepository;
    private final PublishLogRepository logRepository;

    public AppDataController(CurrentUserService currentUserService,
                             SocialAccountService socialAccountService,
                             GeneratedPostRepository postRepository,
                             ScheduledPostRepository scheduledRepository,
                             PublishLogRepository logRepository) {
        this.currentUserService = currentUserService;
        this.socialAccountService = socialAccountService;
        this.postRepository = postRepository;
        this.scheduledRepository = scheduledRepository;
        this.logRepository = logRepository;
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        UUID userId = currentUserService.requireCurrentUserId();
        List<Map<String, Object>> postData = postData(userId);
        long scheduled = postData.stream().filter(p -> "Scheduled".equals(p.get("status"))).count();
        long published = postData.stream().filter(p -> "Published".equals(p.get("status"))).count();
        long failed = postData.stream().filter(p -> "Failed".equals(p.get("status"))).count();
        List<Map<String, Object>> channels = socialAccountService.listCurrentUserAccounts().stream()
                .map(this::channel)
                .toList();
        return ApiResponse.ok(Map.of(
                "totalPosts", postData.size(),
                "scheduledPosts", scheduled,
                "publishedPosts", published,
                "failedPosts", failed,
                "views", 0,
                "likes", 0,
                "comments", 0,
                "shares", 0,
                "channels", channels,
                "recentPosts", postData.stream().limit(10).toList()
        ));
    }

    @GetMapping("/posts")
    public ApiResponse<List<Map<String, Object>>> posts() {
        return ApiResponse.ok(postData(currentUserService.requireCurrentUserId()));
    }

    @GetMapping("/posts/{postId}/comments")
    public ApiResponse<List<Map<String, Object>>> comments(@PathVariable UUID postId) {
        currentUserService.requireCurrentUser();
        return ApiResponse.ok(List.of());
    }

    @GetMapping("/channels")
    public ApiResponse<List<Map<String, Object>>> channels() {
        currentUserService.requireCurrentUser();
        return ApiResponse.ok(currentChannels());
    }

    @GetMapping("/publish-logs")
    public ApiResponse<List<Map<String, Object>>> publishLogs() {
        return ApiResponse.ok(logRepository.findAllForUser(currentUserService.requireCurrentUserId()).stream()
                .map(this::logMap).toList());
    }

    private Map<String, Object> logMap(PublishLog log) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", log.getId().toString());
        result.put("scheduledPostId", log.getScheduledPostId().toString());
        result.put("provider", log.getProvider());
        result.put("externalPostId", log.getExternalPostId());
        result.put("status", log.getStatus());
        result.put("errorMessage", log.getErrorMessage());
        result.put("publishedAt", log.getPublishedAt());
        return result;
    }

    private List<Map<String, Object>> currentChannels() {
        return socialAccountService.listCurrentUserAccounts().stream()
                .map(this::channel)
                .toList();
    }

    private Map<String, Object> channel(SocialAccountResponse account) {
        String type = switch (account.provider()) {
            case FACEBOOK -> "Facebook Page";
            case INSTAGRAM -> "Instagram Business";
            case THREADS -> "Threads";
        };
        String badge = switch (account.provider()) {
            case FACEBOOK -> "Facebook";
            case INSTAGRAM -> "Instagram";
            case THREADS -> "Threads";
        };
        Map<String, Object> result = new HashMap<>();
        result.put("id", account.id().toString());
        result.put("accountId", blankToDefault(account.pageId(), account.id().toString()));
        result.put("name", blankToDefault(account.pageName(), badge + " Page"));
        result.put("type", type);
        result.put("badge", badge);
        result.put("followers", "0");
        result.put("color", "#1877F2");
        result.put("active", true);
        return result;
    }

    private List<Map<String, Object>> postData(UUID userId) {
        List<ScheduledPost> schedules = safeSchedules(userId);
        Set<UUID> postsWithSchedules = new HashSet<>();
        safeAllSchedules(userId).forEach(schedule -> {
            if (schedule.getPostId() != null) {
                postsWithSchedules.add(schedule.getPostId());
            }
        });
        Map<UUID, ScheduledPost> latestSchedule = new HashMap<>();
        schedules.forEach(schedule -> {
            if (schedule.getPostId() != null) {
                latestSchedule.putIfAbsent(schedule.getPostId(), schedule);
            }
        });
        Map<UUID, PublishLog> latestLog = new HashMap<>();
        safeLogs(userId).forEach(log -> {
            if (log.getScheduledPostId() != null) {
                latestLog.putIfAbsent(log.getScheduledPostId(), log);
            }
        });
        return postRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(post -> !postsWithSchedules.contains(post.getId()) || latestSchedule.containsKey(post.getId()))
                .filter(post -> shouldShowInAppData(post, latestSchedule.get(post.getId())))
                .map(post -> {
                    ScheduledPost schedule = latestSchedule.get(post.getId());
                    try {
                        return postMap(post, schedule, schedule == null ? null : latestLog.get(schedule.getId()));
                    } catch (RuntimeException exception) {
                        log.warn("Could not map app post data. postId={}, scheduleId={}",
                                post.getId(), schedule == null ? null : schedule.getId(), exception);
                        return fallbackPostMap(post);
                    }
                })
                .toList();
    }

    private boolean shouldShowInAppData(GeneratedPost post, ScheduledPost activeSchedule) {
        if (activeSchedule != null) {
            return true;
        }
        PostStatus status = post.getStatus();
        return status == null || status == PostStatus.DRAFT || status == PostStatus.PLANNED;
    }

    private List<ScheduledPost> safeSchedules(UUID userId) {
        try {
            return scheduledRepository.findAllForUser(userId);
        } catch (RuntimeException exception) {
            log.warn("Could not load active schedules for app data. userId={}", userId, exception);
            return List.of();
        }
    }

    private List<ScheduledPost> safeAllSchedules(UUID userId) {
        try {
            return scheduledRepository.findAllIncludingDisconnectedForUser(userId);
        } catch (RuntimeException exception) {
            log.warn("Could not load schedules including disconnected accounts for app data. userId={}", userId, exception);
            return List.of();
        }
    }

    private List<PublishLog> safeLogs(UUID userId) {
        try {
            return logRepository.findAllForUser(userId);
        } catch (RuntimeException exception) {
            log.warn("Could not load publish logs for app data. userId={}", userId, exception);
            return List.of();
        }
    }

    private Map<String, Object> postMap(GeneratedPost post, ScheduledPost schedule, PublishLog log) {
        String content = content(post);
        String status = statusLabel(schedule == null
                ? (post.getStatus() == null ? null : post.getStatus().name())
                : (schedule.getStatus() == null ? null : schedule.getStatus().name()));
        LocalDateTime time = schedule == null ? post.getCreatedAt() : schedule.getScheduledTime();
        Map<?, ?> facebook = facebookValues(post);
        Map<String, Object> result = new HashMap<>();
        result.put("id", post.getId().toString());
        result.put("platform", platformLabel(schedule));
        result.put("content", content);
        result.put("status", status);
        result.put("time", time == null ? "" : time.toString());
        result.put("viewCount", 0);
        result.put("likeCount", 0);
        result.put("commentCount", 0);
        result.put("shareCount", 0);
        result.put("permalinkUrl", text(facebook, "permalinkUrl"));
        result.put("platformPostId", log == null || log.getExternalPostId() == null ? text(facebook, "platformPostId") : log.getExternalPostId());
        result.put("mediaType", text(facebook, "mediaType"));
        result.put("mediaUrl", post.getImageUrl() == null ? "" : post.getImageUrl());
        result.put("mediaThumbnailUrl", "");
        result.put("mediaTitle", "");
        result.put("mediaUrls", List.of());
        result.put("reactionLikeCount", 0);
        result.put("reactionLoveCount", 0);
        result.put("reactionCareCount", 0);
        result.put("reactionHahaCount", 0);
        result.put("reactionWowCount", 0);
        result.put("reactionSadCount", 0);
        result.put("reactionAngryCount", 0);
        result.put("scheduledTime", schedule == null || schedule.getScheduledTime() == null ? null : schedule.getScheduledTime().toString());
        return result;
    }

    private Map<String, Object> fallbackPostMap(GeneratedPost post) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", post.getId().toString());
        result.put("platform", "Facebook");
        result.put("content", content(post));
        result.put("status", statusLabel(post.getStatus() == null ? null : post.getStatus().name()));
        result.put("time", post.getCreatedAt() == null ? "" : post.getCreatedAt().toString());
        result.put("scheduledTime", null);
        result.put("viewCount", 0);
        result.put("likeCount", 0);
        result.put("commentCount", 0);
        result.put("shareCount", 0);
        result.put("permalinkUrl", "");
        result.put("platformPostId", "");
        result.put("mediaType", "");
        result.put("mediaUrl", post.getImageUrl() == null ? "" : post.getImageUrl());
        result.put("mediaThumbnailUrl", "");
        result.put("mediaTitle", "");
        result.put("mediaUrls", List.of());
        result.put("reactionLikeCount", 0);
        result.put("reactionLoveCount", 0);
        result.put("reactionCareCount", 0);
        result.put("reactionHahaCount", 0);
        result.put("reactionWowCount", 0);
        result.put("reactionSadCount", 0);
        result.put("reactionAngryCount", 0);
        return result;
    }

    private String content(GeneratedPost post) {
        Object value = facebookValues(post);
        if (value instanceof Map<?, ?> map && map.get("content") != null) return map.get("content").toString();
        return value == null ? "" : value.toString();
    }

    private Map<?, ?> facebookValues(GeneratedPost post) {
        if (post.getPlatformContents() == null) {
            return Map.of();
        }
        Object value = post.getPlatformContents().get("FACEBOOK");
        if (!(value instanceof Map<?, ?>)) value = post.getPlatformContents().get("facebook");
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    private String text(Map<?, ?> values, String key) {
        Object value = values.get(key);
        return value == null ? "" : value.toString();
    }

    private String statusLabel(String value) {
        if (value == null || value.isBlank()) return "Draft";
        String normalized = value.toLowerCase();
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private String platformLabel(ScheduledPost schedule) {
        if (schedule == null || schedule.getPlatform() == null) {
            return "Facebook";
        }
        String normalized = schedule.getPlatform().name().toLowerCase();
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
