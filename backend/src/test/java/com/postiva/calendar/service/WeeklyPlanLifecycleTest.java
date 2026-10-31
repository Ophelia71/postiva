package com.postiva.calendar.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.postiva.config.OpenAiProperties;
import com.postiva.auth.service.CurrentUserService;
import com.postiva.calendar.dto.UpdateWeeklyPlanItemRequest;
import com.postiva.calendar.entity.CalendarItem;
import com.postiva.calendar.entity.CalendarItemStatus;
import com.postiva.calendar.entity.ContentCalendar;
import com.postiva.calendar.entity.ContentCalendarStatus;
import com.postiva.calendar.repository.CalendarItemRepository;
import com.postiva.calendar.repository.ContentCalendarRepository;
import com.postiva.common.Platform;
import com.postiva.file.service.FileService;
import com.postiva.post.service.PostService;
import com.postiva.scheduler.ScheduledPostStatus;
import com.postiva.scheduler.dto.SchedulePostRequest;
import com.postiva.scheduler.dto.ScheduledPostResponse;
import com.postiva.scheduler.repository.ScheduledPostRepository;
import com.postiva.scheduler.service.PublishingService;
import com.postiva.social.entity.SocialAccount;
import com.postiva.social.insights.PostInsightsService;
import com.postiva.social.insights.PerformanceAnalyzer;
import com.postiva.social.repository.SocialAccountRepository;
import com.postiva.social.service.SocialAccountService;
import com.postiva.usage.AiUsageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Reviewing a week in Daily: change a day's time, skip a day, approve into the scheduler, delete the plan. */
class WeeklyPlanLifecycleTest {
    private final UUID userId = UUID.randomUUID();
    private final UUID planId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final ContentCalendarRepository plans = mock(ContentCalendarRepository.class);
    private final CalendarItemRepository items = mock(CalendarItemRepository.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final PublishingService publishing = mock(PublishingService.class);
    private final PostService posts = mock(PostService.class);
    private final ScheduledPostRepository scheduledPosts = mock(ScheduledPostRepository.class);
    private final SocialAccountRepository accountRepository = mock(SocialAccountRepository.class);
    private final List<CalendarItem> planItems = new ArrayList<>();
    private WeeklyPlanService service;
    private ContentCalendar plan;

    @BeforeEach
    void setUp() {
        service = new WeeklyPlanService(plans, items, mock(WeeklyPlanGenerator.class), currentUser,
                mock(SocialAccountService.class), accountRepository, posts, publishing, scheduledPosts,
                mock(FileService.class), mock(AiUsageService.class), mock(OpenAiProperties.class),
                mock(PostInsightsService.class), mock(PerformanceAnalyzer.class), new ObjectMapper());
        when(currentUser.requireCurrentUserId()).thenReturn(userId);

        plan = new ContentCalendar();
        ReflectionTestUtils.setField(plan, "id", planId);
        plan.setUserId(userId);
        plan.setSocialAccountId(accountId);
        plan.setStatus(ContentCalendarStatus.READY);
        plan.setTimezone("Asia/Ho_Chi_Minh");
        plan.setStartDate(LocalDate.now());
        plan.setEndDate(LocalDate.now().plusDays(6));
        ReflectionTestUtils.setField(plan, "updatedAt", LocalDateTime.now());
        when(plans.findByIdAndUserId(planId, userId)).thenReturn(Optional.of(plan));
        when(plans.save(any(ContentCalendar.class))).thenAnswer(call -> call.getArgument(0));
        when(items.findByCalendarIdOrderByPlannedDateAsc(planId)).thenAnswer(call -> List.copyOf(planItems));
        when(items.findByIdAndCalendarId(any(), any())).thenAnswer(call -> planItems.stream()
                .filter(item -> item.getId().equals(call.getArgument(0))).findFirst());
        when(items.save(any(CalendarItem.class))).thenAnswer(call -> call.getArgument(0));
        when(posts.getPostResponses(any(), any())).thenReturn(java.util.Map.of());
        when(scheduledPosts.findAllById(any())).thenReturn(List.of());
        SocialAccount account = new SocialAccount();
        account.setProvider(Platform.FACEBOOK);
        account.setPageName("Geminity Jewelry");
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(publishing.schedule(any())).thenAnswer(call -> {
            SchedulePostRequest request = call.getArgument(0);
            return new ScheduledPostResponse(UUID.randomUUID(), request.postId(), request.socialAccountId(),
                    request.platform(), request.scheduledTime(), ScheduledPostStatus.SCHEDULED, 0, null, null, null);
        });
    }

    // ---- approve

    @Test
    void approveSchedulesEveryDraftAtItsOwnTime() {
        CalendarItem first = draft(Duration.ofDays(1));
        CalendarItem second = draft(Duration.ofDays(2));

        service.approve(planId);

        verify(publishing, times(2)).schedule(any());
        assertEquals(CalendarItemStatus.SCHEDULED, first.getStatus());
        assertEquals(CalendarItemStatus.SCHEDULED, second.getStatus());
        assertEquals(ContentCalendarStatus.APPROVED, plan.getStatus());
    }

    @Test
    void approveSkipsADayWhoseTimeAlreadyPassedInsteadOfPostingLate() {
        CalendarItem late = draft(Duration.ofMinutes(1));
        CalendarItem fine = draft(Duration.ofDays(1));

        service.approve(planId);

        assertEquals(CalendarItemStatus.SKIPPED, late.getStatus());
        assertTrue(late.getError().contains("quá giờ"));
        assertEquals(CalendarItemStatus.SCHEDULED, fine.getStatus());
        verify(publishing, times(1)).schedule(any());
    }

    @Test
    void approveLeavesSkippedDaysAndDaysWithoutADraftAlone() {
        CalendarItem skipped = draft(Duration.ofDays(1));
        skipped.setStatus(CalendarItemStatus.SKIPPED);
        CalendarItem noDraft = draft(Duration.ofDays(2));
        noDraft.setGeneratedPostId(null);
        CalendarItem fine = draft(Duration.ofDays(3));

        service.approve(planId);

        verify(publishing, times(1)).schedule(any());
        assertEquals(CalendarItemStatus.SKIPPED, skipped.getStatus());
        assertEquals(CalendarItemStatus.DRAFT, noDraft.getStatus());
        assertEquals(CalendarItemStatus.SCHEDULED, fine.getStatus());
    }

    @Test
    void oneDayThePlatformRefusesDoesNotBlockTheRestOfTheWeek() {
        CalendarItem refused = draft(Duration.ofDays(1));
        CalendarItem fine = draft(Duration.ofDays(2));
        org.mockito.Mockito.doAnswer(call -> {
            SchedulePostRequest request = call.getArgument(0);
            if (request.postId().equals(refused.getGeneratedPostId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tài khoản không đúng nền tảng");
            }
            return new ScheduledPostResponse(UUID.randomUUID(), request.postId(), request.socialAccountId(),
                    request.platform(), request.scheduledTime(), ScheduledPostStatus.SCHEDULED, 0, null, null, null);
        }).when(publishing).schedule(any());

        service.approve(planId);

        assertEquals(CalendarItemStatus.FAILED, refused.getStatus());
        assertEquals(CalendarItemStatus.SCHEDULED, fine.getStatus());
        assertEquals(ContentCalendarStatus.APPROVED, plan.getStatus());
    }

    @Test
    void approveFailsAndKeepsThePlanOpenWhenNothingCouldBeScheduled() {
        draft(Duration.ofMinutes(1));

        var failure = assertThrows(ResponseStatusException.class, () -> service.approve(planId));

        assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
        assertEquals(ContentCalendarStatus.READY, plan.getStatus());
    }

    @Test
    void aPlanCannotBeApprovedTwiceOrWhileItIsBeingWritten() {
        plan.setStatus(ContentCalendarStatus.APPROVED);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class, () -> service.approve(planId)).getStatusCode());

        plan.setStatus(ContentCalendarStatus.GENERATING);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class, () -> service.approve(planId)).getStatusCode());
        verify(publishing, never()).schedule(any());
    }

    // ---- change a day

    @Test
    void aNewTimeMustBeAtLeast30MinutesAwayAndWithin30Days() {
        CalendarItem item = draft(Duration.ofDays(1));

        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.updateItem(planId, item.getId(), new UpdateWeeklyPlanItemRequest(Instant.now().plus(Duration.ofMinutes(10)), null))).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.updateItem(planId, item.getId(), new UpdateWeeklyPlanItemRequest(Instant.now().plus(Duration.ofDays(31)), null))).getStatusCode());
    }

    @Test
    void aValidNewTimeMovesTheDayAndItsDate() {
        CalendarItem item = draft(Duration.ofDays(1));
        Instant later = Instant.now().plus(Duration.ofDays(5));

        service.updateItem(planId, item.getId(), new UpdateWeeklyPlanItemRequest(later, null));

        assertEquals(later, item.getScheduledTime());
        assertEquals(later.atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate(), item.getPlannedDate());
    }

    @Test
    void aDayCanBeSkippedAndBroughtBack() {
        CalendarItem item = draft(Duration.ofDays(1));

        service.updateItem(planId, item.getId(), new UpdateWeeklyPlanItemRequest(null, true));
        assertEquals(CalendarItemStatus.SKIPPED, item.getStatus());

        service.updateItem(planId, item.getId(), new UpdateWeeklyPlanItemRequest(null, false));
        assertEquals(CalendarItemStatus.DRAFT, item.getStatus());
    }

    @Test
    void aScheduledDayCannotBeSkippedFromThePlan() {
        CalendarItem item = draft(Duration.ofDays(1));
        item.setStatus(CalendarItemStatus.SCHEDULED);

        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.updateItem(planId, item.getId(), new UpdateWeeklyPlanItemRequest(null, true))).getStatusCode());
    }

    @Test
    void anApprovedPlanCannotBeEditedAnymore() {
        CalendarItem item = draft(Duration.ofDays(1));
        plan.setStatus(ContentCalendarStatus.APPROVED);

        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.updateItem(planId, item.getId(), new UpdateWeeklyPlanItemRequest(Instant.now().plus(Duration.ofDays(2)), null))).getStatusCode());
    }

    // ---- delete

    @Test
    void aReadyPlanCanBeDeletedAndItsDaysGoWithIt() {
        draft(Duration.ofDays(1));

        service.delete(planId);

        verify(plans).delete(plan);
        verify(items).deleteAll(any());
    }

    @Test
    void anApprovedPlanCannotBeDeletedButPointsToTheScheduleInstead() {
        plan.setStatus(ContentCalendarStatus.APPROVED);

        var failure = assertThrows(ResponseStatusException.class, () -> service.delete(planId));

        assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
        assertTrue(failure.getReason().contains("Lịch đăng"));
        verify(plans, never()).delete(any());
    }

    @Test
    void aPlanStillBeingWrittenCannotBeDeletedYet() {
        plan.setStatus(ContentCalendarStatus.GENERATING);

        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class, () -> service.delete(planId)).getStatusCode());
    }

    @Test
    void aPlanOfAnotherUserIsNotFound() {
        when(plans.findByIdAndUserId(planId, userId)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> service.approve(planId)).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class, () -> service.delete(planId)).getStatusCode());
        assertNull(plans.findByIdAndUserId(planId, userId).orElse(null));
    }

    private CalendarItem draft(Duration fromNow) {
        CalendarItem item = new CalendarItem();
        ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
        item.setCalendarId(planId);
        item.setGeneratedPostId(UUID.randomUUID());
        item.setPlatform(Platform.FACEBOOK);
        item.setStatus(CalendarItemStatus.DRAFT);
        item.setScheduledTime(Instant.now().plus(fromNow));
        item.setPlannedDate(LocalDate.now().plusDays(1));
        planItems.add(item);
        return item;
    }
}
