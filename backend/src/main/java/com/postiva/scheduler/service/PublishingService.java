package com.postiva.scheduler.service;

import com.postiva.auth.service.CurrentUserService;
import com.postiva.common.Platform;
import com.postiva.common.PostStatus;
import com.postiva.post.entity.GeneratedPost;
import com.postiva.post.repository.GeneratedPostRepository;
import com.postiva.scheduler.ScheduledPostStatus;
import com.postiva.scheduler.dto.PublishNowRequest;
import com.postiva.scheduler.dto.ResolvePublishRequest;
import com.postiva.scheduler.dto.SchedulePostRequest;
import com.postiva.scheduler.dto.ScheduledPostResponse;
import com.postiva.scheduler.entity.ScheduledPost;
import com.postiva.scheduler.repository.ScheduledPostRepository;
import com.postiva.scheduler.service.ScheduledPostStateService.Claim;
import com.postiva.social.entity.SocialAccount;
import com.postiva.social.publishing.PlatformPublisher;
import com.postiva.social.service.SocialAccountService;
import com.postiva.user.entity.User;
import com.postiva.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executor;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PublishingService {
    private final ScheduledPostRepository scheduledRepository;
    private final GeneratedPostRepository postRepository;
    private final SocialAccountService accountService;
    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;
    private final ScheduledPostClaimService claims;
    private final ScheduledPostProcessingService processor;
    private final ScheduledPostStateService states;
    private final Map<Platform, PlatformPublisher> publishers;
    private final TransactionTemplate transactionTemplate;
    private final Executor immediateExecutor;
    private final int batchSize;
    private final Duration lease;

    public PublishingService(ScheduledPostRepository scheduledRepository,
                             GeneratedPostRepository postRepository,
                             SocialAccountService accountService,
                             CurrentUserService currentUserService,
                             UserRepository userRepository,
                             ScheduledPostClaimService claims,
                             ScheduledPostProcessingService processor,
                             ScheduledPostStateService states,
                             List<PlatformPublisher> platformPublishers,
                             PlatformTransactionManager transactionManager,
                             @Qualifier("publishingImmediateExecutor") Executor immediateExecutor,
                             @Value("${postiva.scheduler.batch-size:1}") int batchSize,
                             @Value("${postiva.scheduler.lease-seconds:1800}") long leaseSeconds) {
        this.scheduledRepository = scheduledRepository;
        this.postRepository = postRepository;
        this.accountService = accountService;
        this.currentUserService = currentUserService;
        this.userRepository = userRepository;
        this.claims = claims;
        this.processor = processor;
        this.states = states;
        EnumMap<Platform, PlatformPublisher> publisherMap = new EnumMap<>(Platform.class);
        for (PlatformPublisher publisher : platformPublishers) {
            if (publisherMap.put(publisher.platform(), publisher) != null) {
                throw new IllegalStateException("Có nhiều publisher cho " + publisher.platform());
            }
        }
        this.publishers = Map.copyOf(publisherMap);
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.immediateExecutor = immediateExecutor;
        this.batchSize = Math.max(1, batchSize);
        this.lease = Duration.ofSeconds(Math.max(60, leaseSeconds));
    }

    @Transactional
    public ScheduledPostResponse schedule(SchedulePostRequest request) {
        Instant now = Instant.now();
        if (!request.scheduledTime().isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thời gian đăng phải ở tương lai");
        }
        return response(create(currentUserService.requireCurrentUserId(), request.postId(),
                request.socialAccountId(), request.platform(), request.scheduledTime()));
    }

    /**
     * Schedules a post for a user without an HTTP session (the daily automation). Applies the same
     * ownership, platform and future-time checks as {@link #schedule}.
     */
    @Transactional
    public ScheduledPostResponse scheduleForUser(UUID userId, SchedulePostRequest request) {
        if (!request.scheduledTime().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thời gian đăng phải ở tương lai");
        }
        ScheduledPost scheduled = create(userId, request.postId(), request.socialAccountId(),
                request.platform(), request.scheduledTime());
        // The caller links this row from a JDBC table in the same transaction, so it must exist in the database first.
        scheduledRepository.flush();
        return response(scheduled);
    }

    public ScheduledPostResponse publishNow(PublishNowRequest request) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            ScheduledPost scheduled = create(currentUserService.requireCurrentUserId(), request.postId(),
                    request.socialAccountId(), request.platform(), Instant.now());
            UUID scheduledId = scheduled.getId();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    immediateExecutor.execute(() -> processNow(scheduledId));
                }
            });
            return response(scheduled, ScheduledPostStatus.PUBLISHING);
        }

        UUID scheduledId = transactionTemplate.execute(status -> create(currentUserService.requireCurrentUserId(),
                request.postId(), request.socialAccountId(), request.platform(), Instant.now()).getId());
        if (scheduledId == null) throw new IllegalStateException("Không thể tạo yêu cầu đăng bài");
        processNow(scheduledId);
        return scheduledRepository.findById(scheduledId).map(this::response)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu đăng"));
    }

    private void processNow(UUID scheduledId) {
        claims.claimSpecific(scheduledId, Instant.now(), lease).ifPresent(processor::process);
    }

    private ScheduledPost create(UUID userId, UUID postId, UUID accountId, Platform platform, Instant time) {
        GeneratedPost post = postRepository.findByIdAndUserId(postId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bài viết"));
        SocialAccount account = accountService.requireUserAccount(userId, accountId);
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chỉ có thể thử lại bài đăng đã xác nhận thất bại");
        }
        scheduled.setStatus(ScheduledPostStatus.SCHEDULED);
        scheduled.setScheduledTime(Instant.now());
        scheduled.setNextAttemptAt(null);
        scheduled.setAttemptCount(0);
        scheduled.setClaimToken(null);
        scheduled.setLeaseUntil(null);
        scheduled.setLastError(null);
        scheduled.setFailureCode(null);
        ScheduledPost saved = scheduledRepository.save(scheduled);
        states.refreshPostStatus(saved.getPostId());
        return response(saved);
    }

    @Transactional
    public ScheduledPostResponse resolveUnknown(UUID scheduledPostId, ResolvePublishRequest request) {
        ScheduledPost scheduled = scheduledRepository.findByIdIncludingDisconnectedForUser(
                        scheduledPostId, currentUserService.requireCurrentUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lịch đăng"));
        if (scheduled.getStatus() != ScheduledPostStatus.UNKNOWN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chỉ có thể đối soát bài có kết quả chưa xác định");
        }
        if (request.result() == ResolvePublishRequest.Result.CONFIRMED_PUBLISHED) {
            states.confirmPublished(scheduled, trimToNull(request.externalPostId()), Instant.now());
        } else {
            states.confirmNotPublished(scheduled);
        }
        return response(scheduledRepository.findById(scheduledPostId).orElse(scheduled));
    }

    public void publishDuePosts() {
        List<Claim> due = claims.claimDue(Instant.now(), batchSize, lease);
        for (Claim claim : due) {
            processor.process(claim);
        }
    }

    @Transactional
    public void cancelPendingPostsForUser(UUID userId) {
        List<ScheduledPost> pending = scheduledRepository.findByUserIdAndStatus(
                userId, ScheduledPostStatus.SCHEDULED);
        for (ScheduledPost scheduled : pending) {
            scheduled.setStatus(ScheduledPostStatus.CANCELLED);
            scheduled.setNextAttemptAt(null);
            scheduled.setClaimToken(null);
            scheduled.setLeaseUntil(null);
            scheduled.setLastError("ACCOUNT_LOCKED");
        }
        scheduledRepository.saveAll(pending);
        pending.stream().map(ScheduledPost::getPostId).distinct().forEach(states::refreshPostStatus);
    }

    private ScheduledPostResponse response(ScheduledPost scheduled) {
        return response(scheduled, scheduled.getStatus());
    }

    private ScheduledPostResponse response(ScheduledPost scheduled, ScheduledPostStatus status) {
        return new ScheduledPostResponse(
                scheduled.getId(), scheduled.getPostId(), scheduled.getSocialAccountId(),
                scheduled.getPlatform(), scheduled.getScheduledTime(), status,
                scheduled.getAttemptCount() == null ? 0 : scheduled.getAttemptCount(),
                scheduled.getNextAttemptAt(), scheduled.getLastError(), scheduled.getPublishedAt()
        );
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
