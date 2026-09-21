package com.postiva.social.service;

import com.postiva.auth.service.CurrentUserService;
import com.postiva.common.Platform;
import com.postiva.common.PostStatus;
import com.postiva.file.service.FileService;
import com.postiva.post.entity.GeneratedPost;
import com.postiva.post.entity.ProductBrief;
import com.postiva.post.repository.GeneratedPostRepository;
import com.postiva.post.repository.ProductBriefRepository;
import com.postiva.scheduler.dto.PublishNowRequest;
import com.postiva.scheduler.dto.SchedulePostRequest;
import com.postiva.scheduler.dto.ScheduledPostResponse;
import com.postiva.scheduler.service.PublishingService;
import com.postiva.social.dto.SocialPostRequest;
import com.postiva.social.dto.SocialPostResponse;
import com.postiva.social.dto.SocialPostTargetRequest;
import com.postiva.social.dto.SocialPostTargetResponse;
import com.postiva.social.entity.SocialAccount;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class SocialPostService {
    private static final String TITLE_KEY = "_title";

    private final CurrentUserService currentUserService;
    private final SocialAccountService accountService;
    private final ProductBriefRepository briefRepository;
    private final GeneratedPostRepository postRepository;
    private final FileService fileService;
    private final PublishingService publishingService;

    public SocialPostService(CurrentUserService currentUserService,
                             SocialAccountService accountService,
                             ProductBriefRepository briefRepository,
                             GeneratedPostRepository postRepository,
                             FileService fileService,
                             PublishingService publishingService) {
        this.currentUserService = currentUserService;
        this.accountService = accountService;
        this.briefRepository = briefRepository;
        this.postRepository = postRepository;
        this.fileService = fileService;
        this.publishingService = publishingService;
    }

    public SocialPostResponse create(SocialPostRequest request) {
        UUID userId = currentUserService.requireCurrentUserId();
        LocalDateTime scheduledTime = validateAndConvertTime(request);
        List<SocialPostTargetRequest> targets = validateTargets(request.targets());
        List<Platform> platforms = targets.stream()
                .map(SocialPostTargetRequest::platform)
                .distinct()
                .toList();
        Map<String, Object> normalizedContents = normalizePlatformContents(
                request.platformContents(), platforms);

        GeneratedPost post = request.postId() == null
                ? createPost(userId, request, platforms, normalizedContents)
                : updatePost(userId, request, normalizedContents);
        post.setStatus(PostStatus.SCHEDULED);
        post = postRepository.save(post);

        String firstMediaUrl = fileService.replacePostMedia(
                request.mediaIds(), userId, post.getBriefId(), post.getId());
        post.setImageUrl(firstMediaUrl);
        post = postRepository.save(post);

        List<SocialPostTargetResponse> results = new ArrayList<>();
        for (SocialPostTargetRequest target : targets) {
            ScheduledPostResponse scheduled = scheduledTime == null
                    ? publishingService.publishNow(new PublishNowRequest(
                            post.getId(), target.socialAccountId(), target.platform()))
                    : publishingService.schedule(new SchedulePostRequest(
                            post.getId(), target.socialAccountId(), target.platform(), scheduledTime));
            results.add(new SocialPostTargetResponse(
                    target.socialAccountId(),
                    target.platform(),
                    scheduled.id(),
                    scheduled.status(),
                    scheduled.lastError()
            ));
        }
        return new SocialPostResponse(post.getId(), results);
    }

    private LocalDateTime validateAndConvertTime(SocialPostRequest request) {
        if (request.scheduledTime() == null) {
            return null;
        }
        LocalDateTime value = request.scheduledTime()
                .atZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime();
        if (!value.isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thời gian đăng phải ở tương lai");
        }
        return value;
    }

    private List<SocialPostTargetRequest> validateTargets(List<SocialPostTargetRequest> targets) {
        Set<UUID> accountIds = new LinkedHashSet<>();
        for (SocialPostTargetRequest target : targets) {
            if (!accountIds.add(target.socialAccountId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Một tài khoản không thể được chọn hai lần");
            }
            SocialAccount account = accountService.requireCurrentUserAccount(target.socialAccountId());
            if (account.getProvider() != target.platform()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Tài khoản " + account.getPageName() + " không thuộc nền tảng " + target.platform().name()
                );
            }
        }
        return List.copyOf(targets);
    }

    private GeneratedPost createPost(UUID userId,
                                     SocialPostRequest request,
                                     List<Platform> platforms,
                                     Map<String, Object> contents) {
        ProductBrief brief = new ProductBrief();
        brief.setUserId(userId);
        brief.setProductName(blankToDefault(request.title(), "Bài đăng đa nền tảng"));
        brief.setIndustryCode("GENERAL");
        brief.setTargetAudience("Social followers");
        brief.setGoal("Publish social post");
        brief.setTone("User provided");
        brief.setPlatforms(platforms);
        brief = briefRepository.save(brief);

        GeneratedPost post = new GeneratedPost();
        post.setBriefId(brief.getId());
        post.setUserId(userId);
        post.setPlatformContents(withTitle(contents, request.title()));
        post.setHashtags(normalizeHashtags(request.hashtags()));
        post.setCta(trimToNull(request.cta()));
        return post;
    }

    private GeneratedPost updatePost(UUID userId,
                                     SocialPostRequest request,
                                     Map<String, Object> contents) {
        GeneratedPost post = postRepository.findByIdAndUserId(request.postId(), userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bài viết"));
        Map<String, Object> merged = new LinkedHashMap<>();
        if (post.getPlatformContents() != null) {
            merged.putAll(post.getPlatformContents());
        }
        merged.putAll(contents);
        post.setPlatformContents(withTitle(merged, request.title()));
        post.setHashtags(normalizeHashtags(request.hashtags()));
        post.setCta(trimToNull(request.cta()));
        return post;
    }

    private Map<String, Object> normalizePlatformContents(Map<String, Object> requested,
                                                          List<Platform> platforms) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Platform platform : platforms) {
            Object raw = requested.get(platform.name());
            if (!(raw instanceof Map<?, ?>)) {
                raw = requested.get(platform.name().toLowerCase());
            }
            if (!(raw instanceof Map<?, ?> values)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Thiếu nội dung cho " + platformLabel(platform));
            }
            String preferredField = platform == Platform.INSTAGRAM ? "caption" : "content";
            String fallbackField = platform == Platform.INSTAGRAM ? "content" : "caption";
            String content = text(values.get(preferredField));
            if (content.isBlank()) {
                content = text(values.get(fallbackField));
            }
            if (content.isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Nội dung " + platformLabel(platform) + " không được để trống");
            }
            Map<String, Object> normalized = new LinkedHashMap<>();
            String title = text(values.get("title"));
            if (!title.isBlank()) {
                normalized.put("title", title);
            }
            normalized.put(preferredField, content);
            String mediaType = text(values.get("mediaType"));
            if (!mediaType.isBlank()) {
                normalized.put("mediaType", mediaType.toUpperCase());
            }
            result.put(platform.name(), normalized);
        }
        return result;
    }

    private Map<String, Object> withTitle(Map<String, Object> contents, String title) {
        Map<String, Object> result = new LinkedHashMap<>(contents);
        String normalizedTitle = trimToNull(title);
        if (normalizedTitle != null) {
            result.put(TITLE_KEY, normalizedTitle);
        }
        return result;
    }

    private List<String> normalizeHashtags(List<String> hashtags) {
        if (hashtags == null) {
            return List.of();
        }
        return hashtags.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .map(value -> value.startsWith("#") ? value : "#" + value.replaceFirst("^#+", ""))
                .distinct()
                .limit(30)
                .toList();
    }

    private String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String platformLabel(Platform platform) {
        return switch (platform) {
            case FACEBOOK -> "Facebook";
            case INSTAGRAM -> "Instagram";
            case THREADS -> "Threads";
        };
    }
}
