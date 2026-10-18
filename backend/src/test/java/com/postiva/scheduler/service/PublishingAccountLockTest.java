package com.postiva.scheduler.service;

import com.postiva.auth.service.CurrentUserService;
import com.postiva.common.Platform;
import com.postiva.common.PostStatus;
import com.postiva.file.service.FileService;
import com.postiva.post.entity.GeneratedPost;
import com.postiva.post.repository.GeneratedPostRepository;
import com.postiva.scheduler.ScheduledPostStatus;
import com.postiva.scheduler.entity.PublishLog;
import com.postiva.scheduler.entity.ScheduledPost;
import com.postiva.scheduler.repository.PublishLogRepository;
import com.postiva.scheduler.repository.ScheduledPostRepository;
import com.postiva.scheduler.service.ScheduledPostStateService.Claim;
import com.postiva.social.publishing.PlatformPublisher;
import com.postiva.social.service.SocialAccountService;
import com.postiva.user.entity.User;
import com.postiva.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PublishingAccountLockTest {
    private final ScheduledPostRepository schedules = mock(ScheduledPostRepository.class);
    private final GeneratedPostRepository posts = mock(GeneratedPostRepository.class);
    private final SocialAccountService accounts = mock(SocialAccountService.class);
    private final PublishLogRepository logs = mock(PublishLogRepository.class);
    private final FileService files = mock(FileService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final PlatformPublisher publisher = mock(PlatformPublisher.class);

    @Test
    void backgroundPublisherCancelsLockedUsersPostWithoutCallingExternalProvider() {
        UUID userId = UUID.randomUUID();
        GeneratedPost post = post(userId);
        ScheduledPost scheduled = schedule(post.getId());
        UUID claimToken = UUID.randomUUID();
        scheduled.setClaimToken(claimToken);
        scheduled.setStatus(ScheduledPostStatus.PUBLISHING);
        User locked = new User();
        locked.lock(UUID.randomUUID(), "Tạm khóa");
        when(users.findById(userId)).thenReturn(Optional.of(locked));
        when(posts.findById(post.getId())).thenReturn(Optional.of(post));
        when(schedules.findByIdAndClaimTokenAndStatus(
                scheduled.getId(), claimToken, ScheduledPostStatus.PUBLISHING)).thenReturn(Optional.of(scheduled));
        when(schedules.findByPostId(post.getId())).thenReturn(List.of(scheduled));

        processor().process(new Claim(scheduled.getId(), claimToken));

        assertEquals(ScheduledPostStatus.CANCELLED, scheduled.getStatus());
        assertEquals("ACCOUNT_LOCKED", scheduled.getLastError());
        assertEquals(0, scheduled.getAttemptCount());
        assertEquals(PostStatus.DRAFT, post.getStatus());
        verifyNoInteractions(accounts, files, logs);
        verify(publisher, never()).publish(any(), any(), any(), any());
    }

    @Test
    void lockingCancelsOnlyPendingSchedulesAndKeepsTheirContent() {
        UUID userId = UUID.randomUUID();
        GeneratedPost post = post(userId);
        ScheduledPost scheduled = schedule(post.getId());
        when(schedules.findByUserIdAndStatus(userId, ScheduledPostStatus.SCHEDULED))
                .thenReturn(List.of(scheduled));
        when(posts.findById(post.getId())).thenReturn(Optional.of(post));
        when(schedules.findByPostId(post.getId())).thenReturn(List.of(scheduled));

        service().cancelPendingPostsForUser(userId);

        assertEquals(ScheduledPostStatus.CANCELLED, scheduled.getStatus());
        assertEquals(post.getId(), scheduled.getPostId());
        assertEquals(PostStatus.DRAFT, post.getStatus());
        verify(logs, never()).save(any(PublishLog.class));
    }

    private ScheduledPostProcessingService processor() {
        when(publisher.platform()).thenReturn(Platform.FACEBOOK);
        ScheduledPostStateService stateService = new ScheduledPostStateService(
                schedules, logs, posts, mock(JdbcTemplate.class));
        return new ScheduledPostProcessingService(schedules, posts, accounts, users, files,
                stateService, List.of(publisher), 4, 60, 3600);
    }

    private PublishingService service() {
        when(publisher.platform()).thenReturn(Platform.FACEBOOK);
        return new PublishingService(schedules, posts, accounts, mock(CurrentUserService.class), users,
                mock(ScheduledPostClaimService.class), mock(ScheduledPostProcessingService.class),
                new ScheduledPostStateService(schedules, logs, posts, mock(JdbcTemplate.class)),
                List.of(publisher), mock(PlatformTransactionManager.class), Runnable::run, 1, 1800);
    }

    private GeneratedPost post(UUID userId) {
        GeneratedPost post = new GeneratedPost();
        ReflectionTestUtils.setField(post, "id", UUID.randomUUID());
        post.setUserId(userId);
        post.setStatus(PostStatus.SCHEDULED);
        return post;
    }

    private ScheduledPost schedule(UUID postId) {
        ScheduledPost scheduled = new ScheduledPost();
        ReflectionTestUtils.setField(scheduled, "id", UUID.randomUUID());
        scheduled.setPostId(postId);
        scheduled.setPlatform(Platform.FACEBOOK);
        scheduled.setScheduledTime(Instant.now().minusSeconds(60));
        scheduled.setStatus(ScheduledPostStatus.SCHEDULED);
        return scheduled;
    }
}
