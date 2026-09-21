package com.postiva.appdata.controller;

import com.postiva.auth.service.CurrentUserService;
import com.postiva.common.ApiResponse;
import com.postiva.common.Platform;
import com.postiva.common.PostStatus;
import com.postiva.social.dto.SocialAccountResponse;
import com.postiva.social.service.SocialAccountService;
import com.postiva.post.entity.GeneratedPost;
import com.postiva.post.repository.GeneratedPostRepository;
import com.postiva.scheduler.entity.ScheduledPost;
import com.postiva.scheduler.ScheduledPostStatus;
import com.postiva.scheduler.repository.ScheduledPostRepository;
import com.postiva.scheduler.repository.PublishLogRepository;
import com.postiva.scheduler.entity.PublishLog;
import com.postiva.social.client.FacebookGraphClient;
import com.postiva.social.entity.SocialAccount;
import com.postiva.file.entity.UploadedFile;
import com.postiva.file.service.FileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.time.LocalDateTime;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/app-data")
public class AppDataController {
    private static final Logger log = LoggerFactory.getLogger(AppDataController.class);

    private final CurrentUserService currentUserService;
    private final SocialAccountService socialAccountService;
    private final GeneratedPostRepository postRepository;
    private final ScheduledPostRepository scheduledRepository;
    private final PublishLogRepository logRepository;
    private final FacebookGraphClient facebookGraphClient;
    private final FileService fileService;

    public AppDataController(CurrentUserService currentUserService,
                             SocialAccountService socialAccountService,
                             GeneratedPostRepository postRepository,
                             ScheduledPostRepository scheduledRepository,
                             PublishLogRepository logRepository,
                             FacebookGraphClient facebookGraphClient,
                             FileService fileService) {
        this.currentUserService = currentUserService;
        this.socialAccountService = socialAccountService;
        this.postRepository = postRepository;
        this.scheduledRepository = scheduledRepository;
        this.logRepository = logRepository;
        this.facebookGraphClient = facebookGraphClient;
        this.fileService = fileService;
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

    @PutMapping("/posts/{postId}")
    @Transactional
    public ApiResponse<Map<String, Object>> updatePost(
            @PathVariable UUID postId,
            @RequestBody UpdateAppPostRequest request
    ) {
        UUID userId = currentUserService.requireCurrentUserId();
        String message = request == null ? "" : request.message();
        if (message == null || message.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nội dung bài đăng là bắt buộc");
        }

        GeneratedPost post = postRepository.findByIdAndUserId(postId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bài viết"));

        ScheduledPost targetSchedule = resolveTargetSchedule(userId, postId, request.scheduleId());
        Platform platform = targetSchedule != null && targetSchedule.getPlatform() != null
                ? targetSchedule.getPlatform()
                : request.platform() == null ? Platform.FACEBOOK : request.platform();
        if (targetSchedule != null
                && targetSchedule.getStatus() == ScheduledPostStatus.PUBLISHED
                && platform != Platform.FACEBOOK) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "API hiện chưa hỗ trợ sửa bài " + platformLabel(platform) + " sau khi đã đăng"
            );
        }

        Map<String, Object> contents = new LinkedHashMap<>();
        if (post.getPlatformContents() != null) {
            contents.putAll(post.getPlatformContents());
        }

        Object existingPlatform = contents.get(platform.name());
        if (!(existingPlatform instanceof Map<?, ?>)) {
            existingPlatform = contents.get(platform.name().toLowerCase());
        }

        Map<String, Object> platformContent = new LinkedHashMap<>();
        if (existingPlatform instanceof Map<?, ?> values) {
            values.forEach((key, value) -> {
                if (key != null) {
                    platformContent.put(key.toString(), value);
                }
            });
        }
        platformContent.put(platform == Platform.INSTAGRAM ? "caption" : "content", message.trim());
        contents.put(platform.name(), platformContent);
        contents.remove(platform.name().toLowerCase());
        post.setPlatformContents(contents);

        if (platform == Platform.FACEBOOK) {
            syncPublishedFacebookPost(userId, postId, targetSchedule, message.trim());
        }

        post = postRepository.save(post);

        return ApiResponse.ok(Map.of(
                "id", post.getId().toString(),
                "content", message.trim(),
                "platform", platform.name()
        ));
    }

    @DeleteMapping("/posts/{postId}")
    @Transactional
    public ApiResponse<Map<String, Object>> deletePost(@PathVariable UUID postId) {
        UUID userId = currentUserService.requireCurrentUserId();
        GeneratedPost post = postRepository.findByIdAndUserId(postId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bài viết"));

        List<ScheduledPost> publishedSchedules = safeAllSchedules(userId).stream()
                .filter(schedule -> postId.equals(schedule.getPostId()))
                .filter(schedule -> schedule.getStatus() == ScheduledPostStatus.PUBLISHED)
                .toList();
        List<String> unsupportedPlatforms = publishedSchedules.stream()
                .map(ScheduledPost::getPlatform)
                .filter(platform -> platform != null && platform != Platform.FACEBOOK)
                .map(this::platformLabel)
                .distinct()
                .toList();
        if (!unsupportedPlatforms.isEmpty()) {
            String message = "Chưa thể xóa bài đã đăng trên " + String.join(", ", unsupportedPlatforms)
                    + " qua API. Bài trong Postiva được giữ lại để tránh mất dấu nội dung đang còn trên nền tảng.";
            return ApiResponse.ok(Map.of(
                    "deleted", false,
                    "facebookDeleted", false,
                    "facebookMessage", message,
                    "message", message
            ));
        }

        String facebookDeleteError = deletePublishedFacebookPost(userId, postId);
        if (facebookDeleteError != null) {
            return ApiResponse.ok(Map.of(
                    "deleted", false,
                    "facebookDeleted", false,
                    "facebookMessage", facebookDeleteError,
                    "message", facebookDeleteError
            ));
        }

        postRepository.delete(post);
        postRepository.flush();
        return ApiResponse.ok(Map.of(
                "deleted", true,
                "facebookDeleted", true,
                "facebookMessage", "",
                "message", ""
        ));
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
        result.put("color", channelColor(account.provider()));
        result.put("active", true);
        return result;
    }

    private String channelColor(com.postiva.common.Platform provider) {
        return switch (provider) {
            case FACEBOOK -> "#1877F2";
            case INSTAGRAM -> "#E1306C";
            case THREADS -> "#111827";
        };
    }

    private List<Map<String, Object>> postData(UUID userId) {
        List<ScheduledPost> schedules = safeSchedules(userId);
        Set<UUID> postsWithSchedules = new HashSet<>();
        safeAllSchedules(userId).forEach(schedule -> {
            if (schedule.getPostId() != null) {
                postsWithSchedules.add(schedule.getPostId());
            }
        });
        Map<UUID, List<ScheduledPost>> schedulesByPost = new LinkedHashMap<>();
        schedules.forEach(schedule -> {
            if (schedule.getPostId() != null) {
                schedulesByPost.computeIfAbsent(schedule.getPostId(), ignored -> new ArrayList<>())
                        .add(schedule);
            }
        });
        Map<UUID, PublishLog> latestLog = new HashMap<>();
        safeLogs(userId).forEach(log -> {
            if (log.getScheduledPostId() != null) {
                latestLog.putIfAbsent(log.getScheduledPostId(), log);
            }
        });
        Map<UUID, List<UploadedFile>> mediaByPost = new HashMap<>();
        return postRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .flatMap(post -> {
                    List<ScheduledPost> postSchedules = schedulesByPost.getOrDefault(post.getId(), List.of());
                    List<UploadedFile> mediaFiles = mediaByPost.computeIfAbsent(
                            post.getId(), ignored -> fileService.allMediaForPost(post.getId()));
                    if (!postSchedules.isEmpty()) {
                        return postSchedules.stream().map(schedule -> safePostMap(
                                post, schedule, latestLog.get(schedule.getId()), mediaFiles));
                    }
                    if (postsWithSchedules.contains(post.getId()) || !shouldShowInAppData(post)) {
                        return Stream.empty();
                    }
                    return Stream.of(safePostMap(post, null, null, mediaFiles));
                })
                .toList();
    }

    private Map<String, Object> safePostMap(GeneratedPost post,
                                            ScheduledPost schedule,
                                            PublishLog publishLog,
                                            List<UploadedFile> mediaFiles) {
        try {
            return postMap(post, schedule, publishLog, mediaFiles);
        } catch (RuntimeException exception) {
            log.warn("Could not map app post data. postId={}, scheduleId={}",
                    post.getId(), schedule == null ? null : schedule.getId(), exception);
            return fallbackPostMap(post, schedule, mediaFiles);
        }
    }

    private boolean shouldShowInAppData(GeneratedPost post) {
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

    private Map<String, Object> postMap(GeneratedPost post,
                                        ScheduledPost schedule,
                                        PublishLog log,
                                        List<UploadedFile> mediaFiles) {
        Platform platform = schedule == null || schedule.getPlatform() == null
                ? firstContentPlatform(post)
                : schedule.getPlatform();
        String content = content(post, platform);
        String status = statusLabel(schedule == null
                ? (post.getStatus() == null ? null : post.getStatus().name())
                : (schedule.getStatus() == null ? null : schedule.getStatus().name()));
        LocalDateTime time = schedule == null ? post.getCreatedAt() : schedule.getScheduledTime();
        Map<?, ?> platformValues = platformValues(post, platform);
        List<String> mediaUrls = mediaUrls(post, mediaFiles);
        Map<String, Object> result = new HashMap<>();
        result.put("id", post.getId().toString());
        result.put("scheduleId", schedule == null ? "" : schedule.getId().toString());
        result.put("platform", platformLabel(platform));
        result.put("content", content);
        result.put("status", status);
        result.put("time", time == null ? "" : time.toString());
        result.put("viewCount", 0);
        result.put("likeCount", 0);
        result.put("commentCount", 0);
        result.put("shareCount", 0);
        result.put("permalinkUrl", text(platformValues, "permalinkUrl"));
        result.put("platformPostId", log == null || log.getExternalPostId() == null ? text(platformValues, "platformPostId") : log.getExternalPostId());
        result.put("mediaType", text(platformValues, "mediaType"));
        result.put("mediaUrl", mediaUrls.isEmpty() ? "" : mediaUrls.get(0));
        result.put("mediaThumbnailUrl", "");
        result.put("mediaTitle", "");
        result.put("mediaUrls", mediaUrls);
        result.put("mediaIds", mediaIds(mediaFiles));
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

    private Map<String, Object> fallbackPostMap(GeneratedPost post,
                                                ScheduledPost schedule,
                                                List<UploadedFile> mediaFiles) {
        List<String> mediaUrls = mediaUrls(post, mediaFiles);
        Platform platform = schedule == null || schedule.getPlatform() == null
                ? firstContentPlatform(post)
                : schedule.getPlatform();
        Map<String, Object> result = new HashMap<>();
        result.put("id", post.getId().toString());
        result.put("scheduleId", schedule == null ? "" : schedule.getId().toString());
        result.put("platform", platformLabel(platform));
        result.put("content", content(post, platform));
        result.put("status", statusLabel(schedule == null
                ? (post.getStatus() == null ? null : post.getStatus().name())
                : (schedule.getStatus() == null ? null : schedule.getStatus().name())));
        LocalDateTime time = schedule == null ? post.getCreatedAt() : schedule.getScheduledTime();
        result.put("time", time == null ? "" : time.toString());
        result.put("scheduledTime", schedule == null || schedule.getScheduledTime() == null
                ? null : schedule.getScheduledTime().toString());
        result.put("viewCount", 0);
        result.put("likeCount", 0);
        result.put("commentCount", 0);
        result.put("shareCount", 0);
        result.put("permalinkUrl", "");
        result.put("platformPostId", "");
        result.put("mediaType", "");
        result.put("mediaUrl", mediaUrls.isEmpty() ? "" : mediaUrls.get(0));
        result.put("mediaThumbnailUrl", "");
        result.put("mediaTitle", "");
        result.put("mediaUrls", mediaUrls);
        result.put("mediaIds", mediaIds(mediaFiles));
        result.put("reactionLikeCount", 0);
        result.put("reactionLoveCount", 0);
        result.put("reactionCareCount", 0);
        result.put("reactionHahaCount", 0);
        result.put("reactionWowCount", 0);
        result.put("reactionSadCount", 0);
        result.put("reactionAngryCount", 0);
        return result;
    }

    private List<String> mediaUrls(GeneratedPost post, List<UploadedFile> mediaFiles) {
        List<String> uploadedUrls = mediaFiles.stream()
                .map(UploadedFile::getFileUrl)
                .filter(url -> url != null && !url.isBlank())
                .toList();
        if (!uploadedUrls.isEmpty()) {
            return uploadedUrls;
        }
        return post.getImageUrl() == null || post.getImageUrl().isBlank()
                ? List.of()
                : List.of(post.getImageUrl());
    }

    private List<String> mediaIds(List<UploadedFile> mediaFiles) {
        return mediaFiles.stream()
                .map(UploadedFile::getId)
                .map(UUID::toString)
                .toList();
    }

    private String content(GeneratedPost post, Platform platform) {
        Map<?, ?> values = platformValues(post, platform);
        Object content = values.get(platform == Platform.INSTAGRAM ? "caption" : "content");
        if (content == null) {
            content = values.get(platform == Platform.INSTAGRAM ? "content" : "caption");
        }
        return content == null ? "" : content.toString();
    }

    private Map<?, ?> platformValues(GeneratedPost post, Platform platform) {
        if (post.getPlatformContents() == null) {
            return Map.of();
        }
        Object value = post.getPlatformContents().get(platform.name());
        if (!(value instanceof Map<?, ?>)) value = post.getPlatformContents().get(platform.name().toLowerCase());
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    private Platform firstContentPlatform(GeneratedPost post) {
        if (post.getPlatformContents() != null) {
            for (Platform platform : List.of(Platform.FACEBOOK, Platform.INSTAGRAM, Platform.THREADS)) {
                if (!platformValues(post, platform).isEmpty()) {
                    return platform;
                }
            }
        }
        return Platform.FACEBOOK;
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

    private String platformLabel(Platform platform) {
        if (platform == null) {
            return "Facebook";
        }
        String normalized = platform.name().toLowerCase();
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private ScheduledPost resolveTargetSchedule(UUID userId, UUID postId, UUID scheduleId) {
        if (scheduleId == null) {
            return null;
        }
        ScheduledPost schedule = scheduledRepository.findByIdForUser(scheduleId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lần đăng"));
        if (!postId.equals(schedule.getPostId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lần đăng không thuộc bài viết này");
        }
        return schedule;
    }

    private void syncPublishedFacebookPost(UUID userId,
                                           UUID postId,
                                           ScheduledPost targetSchedule,
                                           String message) {
        ScheduledPost publishedSchedule = targetSchedule;
        if (publishedSchedule == null) {
            publishedSchedule = safeAllSchedules(userId).stream()
                    .filter(schedule -> postId.equals(schedule.getPostId()))
                    .filter(schedule -> schedule.getPlatform() == Platform.FACEBOOK)
                    .filter(schedule -> schedule.getStatus() == ScheduledPostStatus.PUBLISHED)
                    .findFirst()
                    .orElse(null);
        }
        if (publishedSchedule != null
                && (publishedSchedule.getPlatform() != Platform.FACEBOOK
                || publishedSchedule.getStatus() != ScheduledPostStatus.PUBLISHED)) {
            return;
        }
        if (publishedSchedule == null) {
            return;
        }

        PublishLog publishLog = logRepository
                .findFirstByScheduledPostIdAndStatusOrderByPublishedAtDesc(publishedSchedule.getId(), "SUCCESS")
                .orElse(null);
        if (publishLog == null || publishLog.getExternalPostId() == null || publishLog.getExternalPostId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không tìm thấy ID bài viết Facebook để cập nhật");
        }

        SocialAccount account = socialAccountService.requireActiveAccount(publishedSchedule.getSocialAccountId());
        if (account.getTokenExpiresAt() != null && account.getTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Meta token đã hết hạn, hãy kết nối lại Page");
        }
        if (account.getAccessTokenEncrypted() == null || account.getAccessTokenEncrypted().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Facebook Page chưa có Page Access Token");
        }

        facebookGraphClient.updatePostMessage(
                publishLog.getExternalPostId(),
                socialAccountService.decryptToken(account),
                message
        );
    }

    private String deletePublishedFacebookPost(UUID userId, UUID postId) {
        try {
            ScheduledPost publishedSchedule = safeAllSchedules(userId).stream()
                    .filter(schedule -> postId.equals(schedule.getPostId()))
                    .filter(schedule -> schedule.getPlatform() == Platform.FACEBOOK)
                    .filter(schedule -> schedule.getStatus() == ScheduledPostStatus.PUBLISHED)
                    .findFirst()
                    .orElse(null);
            if (publishedSchedule == null) {
                return null;
            }

            PublishLog publishLog = logRepository
                    .findFirstByScheduledPostIdAndStatusOrderByPublishedAtDesc(publishedSchedule.getId(), "SUCCESS")
                    .orElse(null);
            if (publishLog == null || publishLog.getExternalPostId() == null || publishLog.getExternalPostId().isBlank()) {
                return "Không tìm thấy ID bài viết Facebook để xóa";
            }

            SocialAccount account = socialAccountService.requireActiveAccount(publishedSchedule.getSocialAccountId());
            if (account.getTokenExpiresAt() != null && account.getTokenExpiresAt().isBefore(LocalDateTime.now())) {
                return "Meta token đã hết hạn, hãy kết nối lại Page";
            }
            if (account.getAccessTokenEncrypted() == null || account.getAccessTokenEncrypted().isBlank()) {
                return "Facebook Page chưa có Page Access Token";
            }

            facebookGraphClient.deletePost(
                    publishLog.getExternalPostId(),
                    socialAccountService.decryptToken(account)
            );
            return null;
        } catch (RuntimeException exception) {
            return truncate("Không thể xóa bài trên Facebook: " + exception.getMessage());
        }
    }

    private String truncate(String message) {
        if (message == null || message.isBlank()) {
            return "Không thể xóa bài trên Facebook";
        }
        return message.length() <= 300 ? message : message.substring(0, 300);
    }

    private record UpdateAppPostRequest(String message, Platform platform, UUID scheduleId) {
    }
}
