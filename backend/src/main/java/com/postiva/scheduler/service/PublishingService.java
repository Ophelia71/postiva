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
import com.postiva.social.entity.SocialAccount;
import com.postiva.social.publishing.PlatformPublisher;
import com.postiva.social.service.SocialAccountService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.EnumMap;
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
    private final FileService fileService;
    private final Map<Platform, PlatformPublisher> publishers;

    public PublishingService(ScheduledPostRepository scheduledRepository,
                             PublishLogRepository logRepository,
                             GeneratedPostRepository postRepository,
                             SocialAccountService accountService,
                             CurrentUserService currentUserService,
                             FileService fileService,
                             List<PlatformPublisher> platformPublishers) {
        this.scheduledRepository = scheduledRepository;
        this.logRepository = logRepository;
        this.postRepository = postRepository;
        this.accountService = accountService;
        this.currentUserService = currentUserService;
        this.fileService = fileService;
        EnumMap<Platform, PlatformPublisher> publisherMap = new EnumMap<>(Platform.class);
        for (PlatformPublisher publisher : platformPublishers) {
            if (publisherMap.put(publisher.platform(), publisher) != null) {
                throw new IllegalStateException("Có nhiều publisher cho " + publisher.platform());
            }
        }
        this.publishers = Map.copyOf(publisherMap);
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
        ScheduledPost scheduled = create(
                request.postId(), request.socialAccountId(), request.platform(), LocalDateTime.now());
        publishOne(scheduled);
        return response(scheduled);
    }

    private ScheduledPost create(UUID postId, UUID accountId, Platform platform, LocalDateTime time) {
        UUID userId = currentUserService.requireCurrentUserId();
        GeneratedPost post = postRepository.findByIdAndUserId(postId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bài viết"));
        SocialAccount account = accountService.requireCurrentUserAccount(accountId);
        if (account.getProvider() != platform) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tài khoản không đúng nền tảng");
        }
        if (!publishers.containsKey(platform)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nền tảng chưa có bộ publish");
        }

        ScheduledPost scheduled = new ScheduledPost();
        scheduled.setPostId(postId);
        scheduled.setSocialAccountId(accountId);
        scheduled.setPlatform(platform);
        scheduled.setScheduledTime(time);
        scheduled.setStatus(ScheduledPostStatus.SCHEDULED);
        post.setStatus(PostStatus.SCHEDULED);
        postRepository.save(post);
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
        ScheduledPost saved = scheduledRepository.save(scheduled);
        refreshPostStatus(saved.getPostId());
        return response(saved);
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
        int currentAttempts = scheduled.getAttemptCount() == null ? 0 : scheduled.getAttemptCount();
        scheduled.setAttemptCount(currentAttempts + 1);
        scheduledRepository.save(scheduled);

        PublishLog log = new PublishLog();
        log.setScheduledPostId(scheduled.getId());
        log.setProvider(scheduled.getPlatform().name());
        try {
            GeneratedPost post = postRepository.findById(scheduled.getPostId())
                    .orElseThrow(() -> new IllegalStateException("Bài viết không còn tồn tại"));
            SocialAccount account = accountService.prepareForPublishing(scheduled.getSocialAccountId());
            validateAccount(account, scheduled.getPlatform());

            String accessToken = accountService.decryptToken(account);
            List<UploadedFile> mediaFiles = fileService.allMediaForPost(post.getId());
            PlatformPublisher publisher = publishers.get(scheduled.getPlatform());
            String externalId = publisher.publish(post, account, accessToken, mediaFiles);

            LocalDateTime publishedAt = LocalDateTime.now();
            scheduled.setStatus(ScheduledPostStatus.PUBLISHED);
            scheduled.setPublishedAt(publishedAt);
            scheduled.setLastError(null);
            log.setStatus("SUCCESS");
            log.setExternalPostId(externalId);
            log.setPublishedAt(publishedAt);
        } catch (RuntimeException exception) {
            String error = truncate(errorMessage(exception));
            scheduled.setStatus(ScheduledPostStatus.FAILED);
            scheduled.setLastError(error);
            log.setStatus("FAILED");
            log.setErrorMessage(error);
        }
        scheduledRepository.save(scheduled);
        logRepository.save(log);
        refreshPostStatus(scheduled.getPostId());
    }

    private void validateAccount(SocialAccount account, Platform platform) {
        if (account.getProvider() != platform) {
            throw new IllegalStateException("Tài khoản kết nối không đúng nền tảng " + platform.name());
        }
        if (account.getTokenExpiresAt() != null && account.getTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException(platformLabel(platform) + " access token đã hết hạn, hãy kết nối lại tài khoản");
        }
        if (account.getAccessTokenEncrypted() == null || account.getAccessTokenEncrypted().isBlank()) {
            throw new IllegalStateException(platformLabel(platform) + " chưa có access token");
        }
    }

    private void refreshPostStatus(UUID postId) {
        GeneratedPost post = postRepository.findById(postId).orElse(null);
        if (post == null) {
            return;
        }
        List<ScheduledPost> schedules = scheduledRepository.findByPostId(postId);
        boolean hasPending = schedules.stream().anyMatch(item ->
                item.getStatus() == ScheduledPostStatus.SCHEDULED
                        || item.getStatus() == ScheduledPostStatus.PUBLISHING);
        boolean hasPublished = schedules.stream().anyMatch(item ->
                item.getStatus() == ScheduledPostStatus.PUBLISHED);
        if (hasPending) {
            post.setStatus(PostStatus.SCHEDULED);
        } else if (hasPublished) {
            post.setStatus(PostStatus.PUBLISHED);
        } else if (!schedules.isEmpty()) {
            post.setStatus(PostStatus.FAILED);
        }
        postRepository.save(post);
    }

    private String errorMessage(RuntimeException exception) {
        if (exception instanceof ResponseStatusException statusException
                && statusException.getReason() != null
                && !statusException.getReason().isBlank()) {
            return statusException.getReason();
        }
        return exception.getMessage();
    }

    private String truncate(String message) {
        if (message == null || message.isBlank()) {
            return "Đăng bài thất bại";
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    private String platformLabel(Platform platform) {
        return switch (platform) {
            case FACEBOOK -> "Facebook";
            case INSTAGRAM -> "Instagram";
            case THREADS -> "Threads";
        };
    }

    private ScheduledPostResponse response(ScheduledPost scheduled) {
        return new ScheduledPostResponse(
                scheduled.getId(), scheduled.getPostId(), scheduled.getSocialAccountId(),
                scheduled.getPlatform(), scheduled.getScheduledTime(), scheduled.getStatus(),
                scheduled.getAttemptCount(), scheduled.getLastError(), scheduled.getPublishedAt()
        );
    }
}
