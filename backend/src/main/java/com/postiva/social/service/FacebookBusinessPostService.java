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
import com.postiva.social.dto.FacebookBusinessPostRequest;
import com.postiva.social.dto.FacebookBusinessPostResponse;
import com.postiva.social.entity.SocialAccount;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FacebookBusinessPostService {
    private final CurrentUserService currentUserService;
    private final SocialAccountService accountService;
    private final ProductBriefRepository briefRepository;
    private final GeneratedPostRepository postRepository;
    private final FileService fileService;
    private final PublishingService publishingService;

    public FacebookBusinessPostService(CurrentUserService currentUserService,
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

    @Transactional
    public FacebookBusinessPostResponse create(FacebookBusinessPostRequest request) {
        UUID userId = currentUserService.requireCurrentUserId();
        SocialAccount account = accountService.requireCurrentFacebookPage(request.pageId());

        GeneratedPost post = request.postId() == null
                ? createPost(userId, request)
                : updatePost(userId, request);
        post.setStatus(PostStatus.SCHEDULED);
        post = postRepository.save(post);

        List<UUID> mediaIds = mediaIds(request);
        String attachedImageUrl = fileService.attachToPost(mediaIds, userId, post.getBriefId(), post.getId());
        if (attachedImageUrl != null) {
            post.setImageUrl(attachedImageUrl);
            postRepository.save(post);
        }

        ScheduledPostResponse scheduled = request.scheduledTime() == null
                ? publishingService.publishNow(new PublishNowRequest(post.getId(), account.getId(), Platform.FACEBOOK))
                : publishingService.schedule(new SchedulePostRequest(
                        post.getId(), account.getId(), Platform.FACEBOOK, toLocalTime(request.scheduledTime())));
        return new FacebookBusinessPostResponse(post.getId(), scheduled.id(), scheduled.status().name());
    }

    private GeneratedPost createPost(UUID userId, FacebookBusinessPostRequest request) {
        ProductBrief brief = new ProductBrief();
        brief.setUserId(userId);
        brief.setProductName("Facebook Page post");
        brief.setIndustryCode("GENERAL");
        brief.setTargetAudience("Facebook Page followers");
        brief.setGoal("Publish Facebook post");
        brief.setTone("User provided");
        brief.setPlatforms(List.of(Platform.FACEBOOK));
        brief = briefRepository.save(brief);

        GeneratedPost post = new GeneratedPost();
        post.setBriefId(brief.getId());
        post.setUserId(userId);
        post.setPlatformContents(platformContents(request));
        post.setImageUrl(blankToNull(request.mediaUrl()));
        return post;
    }

    private GeneratedPost updatePost(UUID userId, FacebookBusinessPostRequest request) {
        GeneratedPost post = postRepository.findByIdAndUserId(request.postId(), userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bài viết"));
        Map<String, Object> contents = new LinkedHashMap<>();
        if (post.getPlatformContents() != null) {
            contents.putAll(post.getPlatformContents());
        }
        contents.put("FACEBOOK", platformContents(request).get("FACEBOOK"));
        post.setPlatformContents(contents);
        if (request.mediaUrl() != null && !request.mediaUrl().isBlank()) {
            post.setImageUrl(request.mediaUrl().trim());
        }
        return post;
    }

    private Map<String, Object> platformContents(FacebookBusinessPostRequest request) {
        Map<String, Object> facebook = new LinkedHashMap<>();
        facebook.put("content", request.message().trim());
        facebook.put("mediaType", request.mediaType() == null ? "NONE" : request.mediaType());
        Map<String, Object> contents = new LinkedHashMap<>();
        contents.put("FACEBOOK", facebook);
        return contents;
    }

    private List<UUID> mediaIds(FacebookBusinessPostRequest request) {
        List<UUID> ids = new ArrayList<>();
        if (request.mediaIds() != null) {
            ids.addAll(request.mediaIds());
        }
        if (request.mediaId() != null && !ids.contains(request.mediaId())) {
            ids.add(request.mediaId());
        }
        return ids;
    }

    private LocalDateTime toLocalTime(java.time.OffsetDateTime time) {
        return time.atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
