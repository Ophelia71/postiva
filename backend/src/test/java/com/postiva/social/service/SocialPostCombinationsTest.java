package com.postiva.social.service;

import com.postiva.auth.service.CurrentUserService;
import com.postiva.post.entity.ProductBrief;
import com.postiva.post.repository.ProductBriefRepository;
import com.postiva.common.Platform;
import com.postiva.file.service.FileService;
import com.postiva.post.entity.GeneratedPost;
import com.postiva.post.repository.GeneratedPostRepository;
import com.postiva.scheduler.ScheduledPostStatus;
import com.postiva.scheduler.dto.PublishNowRequest;
import com.postiva.scheduler.dto.ScheduledPostResponse;
import com.postiva.scheduler.service.PublishingService;
import com.postiva.social.dto.SocialPostRequest;
import com.postiva.social.dto.SocialPostResponse;
import com.postiva.social.dto.SocialPostTargetRequest;
import com.postiva.social.entity.SocialAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** One post to three, two or one platform, with or without a schedule, and the ways the request can be wrong. */
class SocialPostCombinationsTest {
    private final UUID userId = UUID.randomUUID();
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final SocialAccountService accounts = mock(SocialAccountService.class);
    private final ProductBriefRepository briefs = mock(ProductBriefRepository.class);
    private final GeneratedPostRepository posts = mock(GeneratedPostRepository.class);
    private final FileService files = mock(FileService.class);
    private final PublishingService publishing = mock(PublishingService.class);
    private final SocialPostService service =
            new SocialPostService(currentUser, accounts, briefs, posts, files, publishing);

    private final SocialAccount facebook = account(Platform.FACEBOOK);
    private final SocialAccount instagram = account(Platform.INSTAGRAM);
    private final SocialAccount threads = account(Platform.THREADS);

    @BeforeEach
    void stubRepositories() {
        when(currentUser.requireCurrentUserId()).thenReturn(userId);
        when(briefs.save(any(ProductBrief.class))).thenAnswer(call -> {
            ProductBrief brief = call.getArgument(0);
            ReflectionTestUtils.setField(brief, "id", UUID.randomUUID());
            return brief;
        });
        when(posts.save(any(GeneratedPost.class))).thenAnswer(call -> {
            GeneratedPost post = call.getArgument(0);
            if (post.getId() == null) {
                ReflectionTestUtils.setField(post, "id", UUID.randomUUID());
            }
            return post;
        });
        for (SocialAccount account : List.of(facebook, instagram, threads)) {
            when(accounts.requireCurrentUserAccount(account.getId())).thenReturn(account);
        }
        when(publishing.publishNowAll(anyList())).thenAnswer(call -> {
            List<PublishNowRequest> requests = call.getArgument(0);
            return requests.stream().map(request -> outcome(request, ScheduledPostStatus.PUBLISHED)).toList();
        });
    }

    @Test
    void threePlatformsPublishAtTheSameTime() {
        SocialPostResponse response = service.create(request(List.of(facebook, instagram, threads), null, null));

        assertEquals(List.of(Platform.FACEBOOK, Platform.INSTAGRAM, Platform.THREADS), platformsPublished());
        assertEquals(3, response.targets().size());
        verify(publishing, never()).schedule(any());
    }

    @Test
    void twoPlatformsPublishOnlyThoseTwo() {
        SocialPostResponse response = service.create(request(List.of(facebook, threads), null, null));

        assertEquals(List.of(Platform.FACEBOOK, Platform.THREADS), platformsPublished());
        assertEquals(2, response.targets().size());
    }

    @Test
    void onePlatformPublishesOnlyThatOne() {
        SocialPostResponse response = service.create(request(List.of(instagram), null, null));

        assertEquals(List.of(Platform.INSTAGRAM), platformsPublished());
        assertEquals(1, response.targets().size());
    }

    @Test
    void aFutureTimeSchedulesEveryPlatformInsteadOfPublishing() {
        OffsetDateTime later = OffsetDateTime.now().plusDays(1);
        when(publishing.schedule(any())).thenAnswer(call -> {
            var request = (com.postiva.scheduler.dto.SchedulePostRequest) call.getArgument(0);
            return new ScheduledPostResponse(UUID.randomUUID(), request.postId(), request.socialAccountId(),
                    request.platform(), request.scheduledTime(), ScheduledPostStatus.SCHEDULED, 0, null, null, null);
        });

        SocialPostResponse response = service.create(request(List.of(facebook, instagram), later, null));

        verify(publishing, times(2)).schedule(any());
        verify(publishing, never()).publishNowAll(anyList());
        assertEquals(2, response.targets().size());
    }

    @Test
    void aTimeInThePastIsRejectedBeforeAnythingIsSaved() {
        var failure = assertThrows(ResponseStatusException.class,
                () -> service.create(request(List.of(facebook), OffsetDateTime.now().minusMinutes(5), null)));

        assertTrue(failure.getReason().contains("tương lai"));
        verifyNoInteractions(briefs, posts, publishing);
    }

    @Test
    void oneFailedPlatformIsReportedPerTargetWhileTheOthersStayPublished() {
        when(publishing.publishNowAll(anyList())).thenAnswer(call -> {
            List<PublishNowRequest> requests = call.getArgument(0);
            return requests.stream().map(request -> request.platform() == Platform.INSTAGRAM
                    ? outcome(request, ScheduledPostStatus.FAILED)
                    : outcome(request, ScheduledPostStatus.PUBLISHED)).toList();
        });

        SocialPostResponse response = service.create(request(List.of(facebook, instagram, threads), null, null));

        assertEquals(ScheduledPostStatus.PUBLISHED, response.targets().get(0).status());
        assertEquals(ScheduledPostStatus.FAILED, response.targets().get(1).status());
        assertEquals(ScheduledPostStatus.PUBLISHED, response.targets().get(2).status());
    }

    @Test
    void theSameAccountCannotBePickedTwice() {
        var request = new SocialPostRequest(null, "t",
                List.of(target(facebook), target(facebook)), contentFor(List.of(facebook)), null, null, null, null);

        var failure = assertThrows(ResponseStatusException.class, () -> service.create(request));

        assertTrue(failure.getReason().contains("hai lần"));
        verifyNoInteractions(publishing);
    }

    @Test
    void anAccountOfAnotherPlatformIsRejected() {
        var request = new SocialPostRequest(null, "t",
                List.of(new SocialPostTargetRequest(instagram.getId(), Platform.FACEBOOK)),
                contentFor(List.of(facebook)), null, null, null, null);

        var failure = assertThrows(ResponseStatusException.class, () -> service.create(request));

        assertTrue(failure.getReason().contains("không thuộc nền tảng"));
        verifyNoInteractions(publishing);
    }

    @Test
    void aSelectedPlatformWithoutContentIsRejected() {
        var request = new SocialPostRequest(null, "t",
                List.of(target(facebook), target(instagram)),
                Map.of("FACEBOOK", Map.of("content", "chỉ có Facebook")), null, null, null, null);

        var failure = assertThrows(ResponseStatusException.class, () -> service.create(request));

        assertTrue(failure.getReason().contains("Thiếu nội dung cho Instagram"));
        verifyNoInteractions(publishing);
    }

    @Test
    void blankContentIsRejected() {
        var request = new SocialPostRequest(null, "t", List.of(target(threads)),
                Map.of("THREADS", Map.of("content", "   ")), null, null, null, null);

        var failure = assertThrows(ResponseStatusException.class, () -> service.create(request));

        assertTrue(failure.getReason().contains("không được để trống"));
    }

    @Test
    void oneImageAndSeveralImagesAreBothHandedToTheMediaStep() {
        UUID image = UUID.randomUUID();
        service.create(request(List.of(facebook), null, List.of(image)));
        ArgumentCaptor<List<UUID>> single = ArgumentCaptor.forClass(List.class);
        verify(files).replacePostMedia(single.capture(), any(), any(), any());
        assertEquals(List.of(image), single.getValue());

        List<UUID> many = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            many.add(UUID.randomUUID());
        }
        service.create(request(List.of(facebook, instagram), null, many));
        ArgumentCaptor<List<UUID>> multiple = ArgumentCaptor.forClass(List.class);
        verify(files, times(2)).replacePostMedia(multiple.capture(), any(), any(), any());
        assertEquals(many, multiple.getAllValues().get(1));
    }

    @Test
    void aPostWithNoImageStillPublishesText() {
        service.create(request(List.of(facebook, threads), null, null));

        verify(files).replacePostMedia(any(), any(), any(), any());
        verify(publishing).publishNowAll(anyList());
    }

    private List<Platform> platformsPublished() {
        ArgumentCaptor<List<PublishNowRequest>> captor = ArgumentCaptor.forClass(List.class);
        verify(publishing).publishNowAll(captor.capture());
        return captor.getValue().stream().map(PublishNowRequest::platform).toList();
    }

    private SocialPostRequest request(List<SocialAccount> selected, OffsetDateTime time, List<UUID> mediaIds) {
        return new SocialPostRequest(null, "Bài thử", selected.stream().map(this::target).toList(),
                contentFor(selected), List.of("#postiva"), "Đặt hàng ngay", mediaIds, time);
    }

    private Map<String, Object> contentFor(List<SocialAccount> selected) {
        Map<String, Object> contents = new java.util.LinkedHashMap<>();
        for (SocialAccount account : selected) {
            contents.put(account.getProvider().name(), Map.of(
                    account.getProvider() == Platform.INSTAGRAM ? "caption" : "content", "Nội dung thử"));
        }
        return contents;
    }

    private SocialPostTargetRequest target(SocialAccount account) {
        return new SocialPostTargetRequest(account.getId(), account.getProvider());
    }

    private ScheduledPostResponse outcome(PublishNowRequest request, ScheduledPostStatus status) {
        return new ScheduledPostResponse(UUID.randomUUID(), request.postId(), request.socialAccountId(),
                request.platform(), Instant.now(), status, 1, null,
                status == ScheduledPostStatus.FAILED ? "Lỗi thử" : null, null);
    }

    private SocialAccount account(Platform platform) {
        SocialAccount account = new SocialAccount();
        ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
        account.setProvider(platform);
        account.setPageName(platform.name());
        return account;
    }
}
