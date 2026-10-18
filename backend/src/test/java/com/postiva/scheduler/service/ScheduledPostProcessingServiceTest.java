package com.postiva.scheduler.service;

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
import com.postiva.social.client.MetaApiException;
import com.postiva.social.entity.SocialAccount;
import com.postiva.social.publishing.PlatformPublisher;
import com.postiva.social.publishing.PublishPreparationException;
import com.postiva.social.service.SocialAccountService;
import com.postiva.user.entity.User;
import com.postiva.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.concurrent.TimeoutException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ScheduledPostProcessingServiceTest {
    private final ScheduledPostRepository schedules = mock(ScheduledPostRepository.class);
    private final GeneratedPostRepository posts = mock(GeneratedPostRepository.class);
    private final SocialAccountService accounts = mock(SocialAccountService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final FileService files = mock(FileService.class);
    private final PublishLogRepository logs = mock(PublishLogRepository.class);
    private final PlatformPublisher publisher = mock(PlatformPublisher.class);

    @Test
    void ambiguousProviderFailureIsNotRetriedAndBecomesUnknown() {
        Fixture fixture = fixture();
        doThrow(new RuntimeException("connection reset after request was sent"))
                .when(publisher).publish(any(), any(), anyString(), anyList());

        fixture.processor().process(fixture.claim());

        assertEquals(ScheduledPostStatus.UNKNOWN, fixture.schedule().getStatus());
        assertNull(fixture.schedule().getNextAttemptAt());
        assertTrue(fixture.schedule().getLastError().contains("Kết quả gửi tới nền tảng chưa xác định"));
        verify(logs).save(argThat(log -> "UNKNOWN".equals(log.getStatus())));
        verify(publisher, times(1)).publish(any(), any(), anyString(), anyList());
    }

    @Test
    void providerTimeoutIsNotRetriedAndAccessTokenIsRedactedFromStoredError() {
        Fixture fixture = fixture();
        doThrow(new RuntimeException("timeout after request https://graph.facebook.com/?access_token=secret-token",
                new TimeoutException("provider timed out")))
                .when(publisher).publish(any(), any(), anyString(), anyList());

        fixture.processor().process(fixture.claim());

        assertEquals(ScheduledPostStatus.UNKNOWN, fixture.schedule().getStatus());
        assertFalse(fixture.schedule().getLastError().contains("secret-token"));
        assertNull(fixture.schedule().getNextAttemptAt());
        verify(publisher, times(1)).publish(any(), any(), anyString(), anyList());
    }

    @Test
    void providerRateLimitSchedulesBoundedBackoff() {
        Fixture fixture = fixture();
        doThrow(new MetaApiException(429, "Meta rate limit"))
                .when(publisher).publish(any(), any(), anyString(), anyList());
        Instant started = Instant.now();

        fixture.processor().process(fixture.claim());

        assertEquals(ScheduledPostStatus.SCHEDULED, fixture.schedule().getStatus());
        assertNotNull(fixture.schedule().getNextAttemptAt());
        assertFalse(fixture.schedule().getNextAttemptAt().isBefore(started.plusSeconds(59)));
        assertTrue(fixture.schedule().getNextAttemptAt().isBefore(started.plusSeconds(62)));
        verify(logs).save(argThat(log -> "RETRY_SCHEDULED".equals(log.getStatus())));
    }

    @Test
    void expiredOrRevokedTokenFailsBeforeProviderPublish() {
        Fixture fixture = fixture();
        when(accounts.prepareForPublishing(fixture.schedule().getSocialAccountId()))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                        "Facebook access token đã hết hạn, hãy kết nối lại tài khoản"));

        fixture.processor().process(fixture.claim());

        assertEquals(ScheduledPostStatus.FAILED, fixture.schedule().getStatus());
        assertNull(fixture.schedule().getNextAttemptAt());
        verify(publisher, never()).publish(any(), any(), anyString(), anyList());
        verify(logs).save(argThat(log -> "FAILED".equals(log.getStatus())));
    }

    @Test
    void missingMediaConfigurationFailsWithActionableErrorInsteadOfUnknown() {
        Fixture fixture = fixture();
        String reason = "Cần cấu hình POSTIVA_MEDIA_SIGNING_SECRET có ít nhất 32 byte để Meta tải media an toàn.";
        doThrow(new PublishPreparationException(new IllegalStateException(reason)))
                .when(publisher).publish(any(), any(), anyString(), anyList());

        fixture.processor().process(fixture.claim());

        assertEquals(ScheduledPostStatus.FAILED, fixture.schedule().getStatus());
        assertEquals(reason, fixture.schedule().getLastError());
        assertNull(fixture.schedule().getNextAttemptAt());
        verify(logs).save(argThat(log -> "FAILED".equals(log.getStatus())));
    }

    @Test
    void timeoutDuringContainerPreparationCanRetryWithoutPublishingTwice() {
        Fixture fixture = fixture();
        doThrow(new PublishPreparationException(new RuntimeException("container timed out",
                new TimeoutException("read timed out"))))
                .when(publisher).publish(any(), any(), anyString(), anyList());
        Instant started = Instant.now();

        fixture.processor().process(fixture.claim());

        assertEquals(ScheduledPostStatus.SCHEDULED, fixture.schedule().getStatus());
        assertFalse(fixture.schedule().getNextAttemptAt().isBefore(started.plusSeconds(59)));
        assertTrue(fixture.schedule().getNextAttemptAt().isBefore(started.plusSeconds(62)));
        verify(logs).save(argThat(log -> "RETRY_SCHEDULED".equals(log.getStatus())));
    }

    @Test
    void preparationRetriesStopAtAttemptLimitAndRedactAccessTokens() {
        Fixture fixture = fixture();
        fixture.schedule().setAttemptCount(4);
        doThrow(new PublishPreparationException(new MetaApiException(503,
                "Cannot create container: access_token=secret-token")))
                .when(publisher).publish(any(), any(), anyString(), anyList());

        fixture.processor().process(fixture.claim());

        assertEquals(ScheduledPostStatus.FAILED, fixture.schedule().getStatus());
        assertNull(fixture.schedule().getNextAttemptAt());
        assertFalse(fixture.schedule().getLastError().contains("secret-token"));
    }

    private Fixture fixture() {
        UUID userId = UUID.randomUUID();
        UUID claimToken = UUID.randomUUID();
        GeneratedPost post = new GeneratedPost();
        ReflectionTestUtils.setField(post, "id", UUID.randomUUID());
        post.setUserId(userId);
        post.setStatus(PostStatus.SCHEDULED);
        SocialAccount account = new SocialAccount();
        ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
        account.setProvider(Platform.FACEBOOK);
        account.setAccessTokenEncrypted("encrypted");
        ScheduledPost schedule = new ScheduledPost();
        ReflectionTestUtils.setField(schedule, "id", UUID.randomUUID());
        schedule.setPostId(post.getId());
        schedule.setSocialAccountId(account.getId());
        schedule.setPlatform(Platform.FACEBOOK);
        schedule.setScheduledTime(Instant.now().minusSeconds(10));
        schedule.setStatus(ScheduledPostStatus.PUBLISHING);
        schedule.setAttemptCount(1);
        schedule.setClaimToken(claimToken);

        when(schedules.findByIdAndClaimTokenAndStatus(schedule.getId(), claimToken, ScheduledPostStatus.PUBLISHING))
                .thenReturn(Optional.of(schedule));
        when(schedules.findByPostId(post.getId())).thenReturn(List.of(schedule));
        when(posts.findById(post.getId())).thenReturn(Optional.of(post));
        when(users.findById(userId)).thenReturn(Optional.of(new User()));
        when(accounts.prepareForPublishing(account.getId())).thenReturn(account);
        when(accounts.decryptToken(account)).thenReturn("token");
        when(files.allMediaForPost(post.getId())).thenReturn(List.of());
        when(publisher.platform()).thenReturn(Platform.FACEBOOK);

        ScheduledPostStateService states = new ScheduledPostStateService(
                schedules, logs, posts, mock(JdbcTemplate.class));
        ScheduledPostProcessingService processor = new ScheduledPostProcessingService(
                schedules, posts, accounts, users, files, states, List.of(publisher), 4, 60, 3600);
        return new Fixture(processor, schedule, new Claim(schedule.getId(), claimToken));
    }

    private record Fixture(ScheduledPostProcessingService processor, ScheduledPost schedule, Claim claim) { }
}
