package com.postiva.scheduler.service;

import com.postiva.auth.service.CurrentUserService;
import com.postiva.common.Platform;
import com.postiva.common.PostStatus;
import com.postiva.file.entity.UploadedFile;
import com.postiva.file.service.FileService;
import com.postiva.post.entity.GeneratedPost;
import com.postiva.post.repository.GeneratedPostRepository;
import com.postiva.scheduler.ScheduledPostStatus;
import com.postiva.scheduler.dto.PublishNowRequest;
import com.postiva.scheduler.dto.SchedulePostRequest;
import com.postiva.scheduler.dto.ScheduledPostResponse;
import com.postiva.scheduler.entity.PublishLog;
import com.postiva.scheduler.entity.ScheduledPost;
import com.postiva.scheduler.repository.PublishLogRepository;
import com.postiva.scheduler.repository.ScheduledPostRepository;
import com.postiva.social.client.FacebookGraphClient;
import com.postiva.social.entity.SocialAccount;
import com.postiva.social.service.SocialAccountService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PublishingService {
    private final ScheduledPostRepository scheduledRepository;
    private final PublishLogRepository logRepository;
    private final GeneratedPostRepository postRepository;
    private final SocialAccountService accountService;
    private final CurrentUserService currentUserService;
    private final FacebookGraphClient facebookClient;
    private final FileService fileService;

    public PublishingService(ScheduledPostRepository scheduledRepository,
                             PublishLogRepository logRepository,
                             GeneratedPostRepository postRepository,
                             SocialAccountService accountService,
                             CurrentUserService currentUserService,
                             FacebookGraphClient facebookClient,
                             FileService fileService) {
        this.scheduledRepository = scheduledRepository;
        this.logRepository = logRepository;
        this.postRepository = postRepository;
        this.accountService = accountService;
        this.currentUserService = currentUserService;
        this.facebookClient = facebookClient;
        this.fileService = fileService;
    }

    @Transactional
    public ScheduledPostResponse schedule(SchedulePostRequest request) {
        if (!request.scheduledTime().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thời gian đăng phải ở tương lai");
        }
        return response(create(request.postId(), request.socialAccountId(), request.platform(), request.scheduledTime()));
    }

    @Transactional
    public ScheduledPostResponse publishNow(PublishNowRequest request) {
        ScheduledPost scheduled = create(request.postId(), request.socialAccountId(), request.platform(), LocalDateTime.now());
        publishOne(scheduled);
        return response(scheduled);
    }

    private ScheduledPost create(UUID postId, UUID accountId, Platform platform, LocalDateTime time) {
        if (platform != Platform.FACEBOOK) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MVP hiện chỉ hỗ trợ đăng Facebook");
        }
        UUID userId = currentUserService.requireCurrentUserId();
        postRepository.findByIdAndUserId(postId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bài viết"));
        SocialAccount account = accountService.requireCurrentUserAccount(accountId);
        if (account.getProvider() != platform) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tài khoản không đúng nền tảng");
        }

        ScheduledPost scheduled = new ScheduledPost();
        scheduled.setPostId(postId);
        scheduled.setSocialAccountId(accountId);
        scheduled.setPlatform(platform);
        scheduled.setScheduledTime(time);
        scheduled.setStatus(ScheduledPostStatus.SCHEDULED);
        return scheduledRepository.save(scheduled);
    }

    @Transactional(readOnly = true)
    public List<ScheduledPostResponse> list() {
        return scheduledRepository.findAllForUser(currentUserService.requireCurrentUserId())
                .stream().map(this::response).toList();
    }

    @Transactional
    public ScheduledPostResponse retry(UUID scheduledPostId) {
        ScheduledPost scheduled = scheduledRepository.findByIdForUser(
                        scheduledPostId, currentUserService.requireCurrentUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lịch đăng"));
        if (scheduled.getStatus() != ScheduledPostStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ có thể thử lại bài đăng thất bại");
        }
        scheduled.setStatus(ScheduledPostStatus.SCHEDULED);
        scheduled.setScheduledTime(LocalDateTime.now());
        scheduled.setLastError(null);
        return response(scheduledRepository.save(scheduled));
    }

    @Transactional
    public void publishDuePosts() {
        List<ScheduledPost> duePosts = scheduledRepository
                .findTop20ByStatusAndScheduledTimeLessThanEqualOrderByScheduledTimeAsc(
                        ScheduledPostStatus.SCHEDULED, LocalDateTime.now());
        for (ScheduledPost scheduled : duePosts) {
            publishOne(scheduled);
        }
    }

    private void publishOne(ScheduledPost scheduled) {
        scheduled.setStatus(ScheduledPostStatus.PUBLISHING);
        scheduled.setAttemptCount(scheduled.getAttemptCount() + 1);
        scheduledRepository.save(scheduled);

        PublishLog log = new PublishLog();
        log.setScheduledPostId(scheduled.getId());
        log.setProvider(scheduled.getPlatform().name());
        try {
            GeneratedPost post = postRepository.findById(scheduled.getPostId())
                    .orElseThrow(() -> new IllegalStateException("Bài viết không còn tồn tại"));
            SocialAccount account = accountService.requireActiveAccount(scheduled.getSocialAccountId());
            if (account.getTokenExpiresAt() != null && account.getTokenExpiresAt().isBefore(LocalDateTime.now())) {
                throw new IllegalStateException("Meta token đã hết hạn, hãy kết nối lại Page");
            }

            String message = facebookContent(post);
            String token = accountService.decryptToken(account);
            UploadedFile image = fileService.firstImageForPost(post.getId());
            String externalId;
            if (image != null) {
                externalId = facebookClient.publishPhoto(account.getPageId(), token, message, fileService.resource(image));
            } else if (isExternalImage(post)) {
                externalId = facebookClient.publishPhotoUrl(account.getPageId(), token, message, post.getImageUrl());
            } else {
                externalId = facebookClient.publishText(account.getPageId(), token, message);
            }

            LocalDateTime publishedAt = LocalDateTime.now();
            scheduled.setStatus(ScheduledPostStatus.PUBLISHED);
            scheduled.setPublishedAt(publishedAt);
            scheduled.setLastError(null);
            post.setStatus(PostStatus.PUBLISHED);
            postRepository.save(post);
            log.setStatus("SUCCESS");
            log.setExternalPostId(externalId);
            log.setPublishedAt(publishedAt);
        } catch (RuntimeException exception) {
            String error = truncate(exception.getMessage());
            scheduled.setStatus(ScheduledPostStatus.FAILED);
            scheduled.setLastError(error);
            log.setStatus("FAILED");
            log.setErrorMessage(error);
        }
        scheduledRepository.save(scheduled);
        logRepository.save(log);
    }

    private String facebookContent(GeneratedPost post) {
        Object facebook = post.getPlatformContents().get("FACEBOOK");
        if (facebook instanceof Map<?, ?> values) {
            Object content = values.get("content");
            if (content != null && !content.toString().isBlank()) {
                return content.toString();
            }
        }
        throw new IllegalStateException("Bài viết chưa có nội dung Facebook");
    }

    private boolean isExternalImage(GeneratedPost post) {
        Object facebook = post.getPlatformContents().get("FACEBOOK");
        if (!(facebook instanceof Map<?, ?> values)) {
            return false;
        }
        Object mediaType = values.get("mediaType");
        return mediaType != null && "IMAGE".equalsIgnoreCase(mediaType.toString())
                && post.getImageUrl() != null
                && (post.getImageUrl().startsWith("https://") || post.getImageUrl().startsWith("http://"));
    }

    private String truncate(String message) {
        if (message == null || message.isBlank()) {
            return "Đăng bài thất bại";
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    private ScheduledPostResponse response(ScheduledPost scheduled) {
        return new ScheduledPostResponse(
                scheduled.getId(), scheduled.getPostId(), scheduled.getSocialAccountId(),
                scheduled.getPlatform(), scheduled.getScheduledTime(), scheduled.getStatus(),
                scheduled.getAttemptCount(), scheduled.getLastError(), scheduled.getPublishedAt()
        );
    }
}
