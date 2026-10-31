package com.postiva.scheduler.service;

import com.postiva.auth.service.CurrentUserService;
import com.postiva.common.Platform;
import com.postiva.post.entity.GeneratedPost;
import com.postiva.post.repository.GeneratedPostRepository;
import com.postiva.scheduler.dto.PublishNowRequest;
import com.postiva.scheduler.entity.ScheduledPost;
import com.postiva.scheduler.repository.ScheduledPostRepository;
import com.postiva.scheduler.service.ScheduledPostStateService.Claim;
import com.postiva.social.entity.SocialAccount;
import com.postiva.social.publishing.PlatformPublisher;
import com.postiva.social.service.SocialAccountService;
import com.postiva.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** "Đăng ngay" to several platforms: each one is processed, and one failing does not stop the others. */
class PublishNowAllParallelTest {
    private final UUID userId = UUID.randomUUID();
    private final ScheduledPostRepository scheduled = mock(ScheduledPostRepository.class);
    private final GeneratedPostRepository posts = mock(GeneratedPostRepository.class);
    private final SocialAccountService accounts = mock(SocialAccountService.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final ScheduledPostClaimService claims = mock(ScheduledPostClaimService.class);
    private final ScheduledPostProcessingService processor = mock(ScheduledPostProcessingService.class);
    private final Map<UUID, ScheduledPost> store = new HashMap<>();
    private final Map<UUID, Platform> platformByScheduledId = new HashMap<>();
    private final PublishingService service;

    PublishNowAllParallelTest() {
        List<PlatformPublisher> publishers = new ArrayList<>();
        for (Platform platform : Platform.values()) {
            PlatformPublisher publisher = mock(PlatformPublisher.class);
            when(publisher.platform()).thenReturn(platform);
            publishers.add(publisher);
        }
        service = new PublishingService(scheduled, posts, accounts, currentUser, mock(UserRepository.class),
                claims, processor, mock(ScheduledPostStateService.class), publishers,
                mock(PlatformTransactionManager.class), Executors.newFixedThreadPool(4), 1, 1800);
        when(currentUser.requireCurrentUserId()).thenReturn(userId);
        when(posts.findByIdAndUserId(any(), any())).thenReturn(Optional.of(new GeneratedPost()));
        when(scheduled.save(any(ScheduledPost.class))).thenAnswer(call -> {
            ScheduledPost post = call.getArgument(0);
            if (post.getId() == null) {
                ReflectionTestUtils.setField(post, "id", UUID.randomUUID());
            }
            store.put(post.getId(), post);
            platformByScheduledId.put(post.getId(), post.getPlatform());
            return post;
        });
        when(scheduled.findById(any())).thenAnswer(call -> Optional.ofNullable(store.get(call.<UUID>getArgument(0))));
        when(claims.claimSpecific(any(), any(Instant.class), any(Duration.class)))
                .thenAnswer(call -> Optional.of(new Claim(call.getArgument(0), UUID.randomUUID())));
    }

    @Test
    void everyChosenPlatformIsProcessedAndAnswersComeBackInOrder() {
        var requests = requests(Platform.FACEBOOK, Platform.INSTAGRAM, Platform.THREADS);

        var answers = service.publishNowAll(requests);

        verify(processor, times(3)).process(any());
        assertEquals(List.of(Platform.FACEBOOK, Platform.INSTAGRAM, Platform.THREADS),
                answers.stream().map(answer -> answer.platform()).toList());
    }

    @Test
    void twoPlatformsProcessOnlyTwo() {
        service.publishNowAll(requests(Platform.FACEBOOK, Platform.THREADS));

        verify(processor, times(2)).process(any());
    }

    @Test
    void onePlatformProcessesOnlyOne() {
        var answers = service.publishNowAll(requests(Platform.INSTAGRAM));

        verify(processor, times(1)).process(any());
        assertEquals(1, answers.size());
    }

    @Test
    void oneFailingPlatformDoesNotStopTheOthers() {
        // The processor throws for the first claim only; the other two must still run.
        doThrow(new IllegalStateException("Meta từ chối")).doNothing().when(processor).process(any());

        var answers = service.publishNowAll(requests(Platform.FACEBOOK, Platform.INSTAGRAM, Platform.THREADS));

        verify(processor, times(3)).process(any());
        assertEquals(3, answers.size());
        assertTrue(answers.stream().allMatch(answer -> answer.id() != null));
    }

    private List<PublishNowRequest> requests(Platform... platforms) {
        UUID postId = UUID.randomUUID();
        List<PublishNowRequest> requests = new ArrayList<>();
        for (Platform platform : platforms) {
            SocialAccount account = new SocialAccount();
            account.setProvider(platform);
            UUID accountId = UUID.randomUUID();
            ReflectionTestUtils.setField(account, "id", accountId);
            when(accounts.requireCurrentUserAccount(accountId)).thenReturn(account);
            requests.add(new PublishNowRequest(postId, accountId, platform));
        }
        return requests;
    }
}
