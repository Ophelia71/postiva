package com.postiva.social.service;

import com.postiva.common.Platform;
import com.postiva.common.PostStatus;
import com.postiva.post.entity.GeneratedPost;
import com.postiva.post.entity.ProductBrief;
import com.postiva.post.repository.GeneratedPostRepository;
import com.postiva.post.repository.ProductBriefRepository;
import com.postiva.scheduler.ScheduledPostStatus;
import com.postiva.scheduler.entity.PublishLog;
import com.postiva.scheduler.entity.ScheduledPost;
import com.postiva.scheduler.repository.PublishLogRepository;
import com.postiva.scheduler.repository.ScheduledPostRepository;
import com.postiva.social.client.FacebookGraphClient;
import com.postiva.social.dto.FacebookSyncResponse;
import com.postiva.social.entity.SocialAccount;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FacebookPostSyncService {
    private final SocialAccountService accountService;
    private final FacebookGraphClient facebookClient;
    private final ProductBriefRepository briefRepository;
    private final GeneratedPostRepository postRepository;
    private final ScheduledPostRepository scheduledRepository;
    private final PublishLogRepository logRepository;

    public FacebookPostSyncService(SocialAccountService accountService,
                                   FacebookGraphClient facebookClient,
                                   ProductBriefRepository briefRepository,
                                   GeneratedPostRepository postRepository,
                                   ScheduledPostRepository scheduledRepository,
                                   PublishLogRepository logRepository) {
        this.accountService = accountService;
        this.facebookClient = facebookClient;
        this.briefRepository = briefRepository;
        this.postRepository = postRepository;
        this.scheduledRepository = scheduledRepository;
        this.logRepository = logRepository;
    }

    @Transactional
    public FacebookSyncResponse sync(String pageId) {
        SocialAccount account = accountService.requireCurrentFacebookPage(pageId);
        accountService.ensureTokenUsable(account);
        List<FacebookGraphClient.FacebookPagePost> pagePosts = facebookClient.getPagePosts(
                account.getPageId(), accountService.decryptToken(account));
        Set<String> livePostIds = pagePosts.stream()
                .map(FacebookGraphClient.FacebookPagePost::id)
                .filter(Objects::nonNull)
                .filter(id -> !id.isBlank())
                .collect(Collectors.toSet());

        int imported = 0;
        for (FacebookGraphClient.FacebookPagePost pagePost : pagePosts) {
            if (logRepository.findFirstByProviderAndExternalPostId("FACEBOOK", pagePost.id()).isPresent()) {
                continue;
            }
            importPost(account, pagePost);
            imported++;
        }
        int removed = removeDeletedFacebookPosts(account, livePostIds);
        return new FacebookSyncResponse(account.getPageId(), imported, removed);
    }

    private int removeDeletedFacebookPosts(SocialAccount account, Set<String> livePostIds) {
        int removed = 0;
        Set<UUID> removedPostIds = new java.util.HashSet<>();
        List<ScheduledPost> localPublishedPosts = scheduledRepository.findBySocialAccountIdAndStatus(
                account.getId(), ScheduledPostStatus.PUBLISHED);
        for (ScheduledPost scheduled : localPublishedPosts) {
            String externalId = externalPostId(scheduled);
            if (!livePostIds.isEmpty() && (externalId == null || externalId.isBlank() || livePostIds.contains(externalId))) {
                continue;
            }
            deleteSyncedPost(scheduled);
            if (scheduled.getPostId() != null) {
                removedPostIds.add(scheduled.getPostId());
            }
            removed++;
        }

        for (GeneratedPost post : postRepository.findAllByUserIdOrderByCreatedAtDesc(account.getUserId())) {
            if (post.getStatus() != PostStatus.PUBLISHED || removedPostIds.contains(post.getId())) {
                continue;
            }
            String externalId = platformPostId(post);
            if (externalId.isBlank() || livePostIds.contains(externalId) || !belongsToPage(externalId, account.getPageId())) {
                continue;
            }
            postRepository.delete(post);
            removed++;
        }
        return removed;
    }

    private void deleteSyncedPost(ScheduledPost scheduled) {
        logRepository.deleteByScheduledPostId(scheduled.getId());
        scheduledRepository.delete(scheduled);
        if (scheduled.getPostId() != null) {
            postRepository.deleteById(scheduled.getPostId());
        }
    }

    private String externalPostId(ScheduledPost scheduled) {
        return logRepository.findFirstByScheduledPostId(scheduled.getId())
                .map(PublishLog::getExternalPostId)
                .filter(value -> value != null && !value.isBlank())
                .orElseGet(() -> platformPostId(scheduled));
    }

    private String platformPostId(ScheduledPost scheduled) {
        return postRepository.findById(scheduled.getPostId())
                .map(GeneratedPost::getPlatformContents)
                .map(this::platformPostId)
                .orElse("");
    }

    private String platformPostId(GeneratedPost post) {
        return platformPostId(post.getPlatformContents());
    }

    private String platformPostId(Map<String, Object> contents) {
        if (contents == null) {
            return "";
        }
        Object facebook = contents.get("FACEBOOK");
        Map<?, ?> values;
        if (facebook instanceof Map<?, ?> facebookValues) {
            values = facebookValues;
        } else {
            facebook = contents.get("facebook");
            if (!(facebook instanceof Map<?, ?> facebookValues)) {
                return "";
            }
            values = facebookValues;
        }
        Object externalId = values.get("platformPostId");
        return externalId == null ? "" : externalId.toString();
    }

    private boolean belongsToPage(String externalId, String pageId) {
        return pageId != null && !pageId.isBlank() && externalId.startsWith(pageId + "_");
    }

    private void importPost(SocialAccount account, FacebookGraphClient.FacebookPagePost source) {
        ProductBrief brief = new ProductBrief();
        brief.setUserId(account.getUserId());
        brief.setProductName("Imported Facebook post");
        brief.setIndustryCode("FACEBOOK_IMPORT");
        brief.setTargetAudience("Facebook Page followers");
        brief.setGoal("Import post history");
        brief.setTone("Imported from Facebook");
        brief.setPlatforms(List.of(Platform.FACEBOOK));
        brief = briefRepository.save(brief);

        Map<String, Object> facebook = new HashMap<>();
        facebook.put("content", source.message());
        facebook.put("platformPostId", source.id());
        facebook.put("permalinkUrl", source.permalinkUrl());
        Map<String, Object> contents = new HashMap<>();
        contents.put("FACEBOOK", facebook);

        GeneratedPost post = new GeneratedPost();
        post.setBriefId(brief.getId());
        post.setUserId(account.getUserId());
        post.setPlatformContents(contents);
        post.setStatus(PostStatus.PUBLISHED);
        post = postRepository.save(post);

        LocalDateTime publishedAt = source.createdAt() == null ? LocalDateTime.now() : source.createdAt();
        ScheduledPost scheduled = new ScheduledPost();
        scheduled.setPostId(post.getId());
        scheduled.setSocialAccountId(account.getId());
        scheduled.setPlatform(Platform.FACEBOOK);
        scheduled.setScheduledTime(publishedAt);
        scheduled.setStatus(ScheduledPostStatus.PUBLISHED);
        scheduled.setAttemptCount(1);
        scheduled.setPublishedAt(publishedAt);
        scheduled = scheduledRepository.save(scheduled);

        PublishLog log = new PublishLog();
        log.setScheduledPostId(scheduled.getId());
        log.setProvider("FACEBOOK");
        log.setExternalPostId(source.id());
        log.setStatus("SUCCESS");
        log.setPublishedAt(publishedAt);
        logRepository.save(log);
    }
}
