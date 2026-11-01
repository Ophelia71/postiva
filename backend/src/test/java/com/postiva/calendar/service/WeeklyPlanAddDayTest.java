package com.postiva.calendar.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.postiva.auth.service.CurrentUserService;
import com.postiva.calendar.dto.AddWeeklyPlanItemRequest;
import com.postiva.calendar.entity.CalendarItem;
import com.postiva.calendar.entity.ContentCalendar;
import com.postiva.calendar.entity.ContentCalendarStatus;
import com.postiva.calendar.repository.CalendarItemRepository;
import com.postiva.calendar.repository.ContentCalendarRepository;
import com.postiva.common.Platform;
import com.postiva.config.OpenAiProperties;
import com.postiva.file.service.FileService;
import com.postiva.post.service.PostService;
import com.postiva.scheduler.repository.ScheduledPostRepository;
import com.postiva.scheduler.service.PublishingService;
import com.postiva.social.entity.SocialAccount;
import com.postiva.social.insights.PerformanceAnalyzer;
import com.postiva.social.insights.PostInsightsService;
import com.postiva.social.repository.SocialAccountRepository;
import com.postiva.social.service.SocialAccountService;
import com.postiva.usage.AiUsageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A shop can add a post on a rest day of a week that is still waiting for review. */
class WeeklyPlanAddDayTest {
    private final UUID userId = UUID.randomUUID();
    private final UUID planId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final ContentCalendarRepository plans = mock(ContentCalendarRepository.class);
    private final CalendarItemRepository items = mock(CalendarItemRepository.class);
    private final WeeklyPlanGenerator generator = mock(WeeklyPlanGenerator.class);
    private final SocialAccountService accounts = mock(SocialAccountService.class);
    private final AiUsageService usage = mock(AiUsageService.class);
    private final OpenAiProperties ai = mock(OpenAiProperties.class);
    private final List<CalendarItem> planItems = new ArrayList<>();
    private WeeklyPlanService service;
    private ContentCalendar plan;

    @BeforeEach
    void setUp() {
        CurrentUserService currentUser = mock(CurrentUserService.class);
        PostService posts = mock(PostService.class);
        service = new WeeklyPlanService(plans, items, generator, currentUser, accounts, mock(SocialAccountRepository.class),
                posts, mock(PublishingService.class), mock(ScheduledPostRepository.class), mock(FileService.class), usage, ai,
                mock(PostInsightsService.class), mock(PerformanceAnalyzer.class), new ObjectMapper());
        when(currentUser.requireCurrentUserId()).thenReturn(userId);

        plan = new ContentCalendar();
        ReflectionTestUtils.setField(plan, "id", planId);
        plan.setUserId(userId);
        plan.setSocialAccountId(accountId);
        plan.setStatus(ContentCalendarStatus.READY);
        plan.setTimezone("Asia/Ho_Chi_Minh");
        plan.setStartDate(LocalDate.now().plusDays(1));
        plan.setEndDate(LocalDate.now().plusDays(7));
        plan.setBrief(Map.of("postsPerWeek", 2, "productName", "Vong tay", "postTime", "09:00"));
        ReflectionTestUtils.setField(plan, "updatedAt", LocalDateTime.now());
        when(plans.findByIdAndUserId(planId, userId)).thenReturn(Optional.of(plan));
        when(plans.save(any(ContentCalendar.class))).thenAnswer(call -> call.getArgument(0));
        when(items.findByCalendarIdOrderByPlannedDateAsc(planId)).thenAnswer(call -> List.copyOf(planItems));
        when(posts.getPostResponses(any(), any())).thenReturn(Map.of());

        SocialAccount account = new SocialAccount();
        account.setProvider(Platform.FACEBOOK);
        when(accounts.requireCurrentUserAccount(accountId)).thenReturn(account);
        when(ai.enabled()).thenReturn(true);
        when(usage.summary(userId)).thenReturn(units(10));
    }

    private static AiUsageService.Summary units(int remaining) {
        return new AiUsageService.Summary(UUID.randomUUID(), "FREE", null, null, 10, 10 - remaining, 0, remaining);
    }

    private CalendarItem existing(LocalDate day) {
        CalendarItem item = new CalendarItem();
        ReflectionTestUtils.setField(item, "id", UUID.randomUUID());
        item.setPlannedDate(day);
        item.setPostType("Gioi thieu san pham");
        planItems.add(item);
        return item;
    }

    @Test
    void restDayGetsADraftAtThePlanTime() {
        LocalDate day = LocalDate.now().plusDays(3);

        service.addItem(planId, new AddWeeklyPlanItemRequest(day));

        verify(generator).addDay(eq(plan), eq(day), any(), eq(Platform.FACEBOOK), any());
    }

    @Test
    void dayThatAlreadyHasAPostIsRefused() {
        LocalDate day = LocalDate.now().plusDays(2);
        existing(day);

        var failure = assertThrows(ResponseStatusException.class, () -> service.addItem(planId, new AddWeeklyPlanItemRequest(day)));

        assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
        verify(generator, never()).addDay(any(), any(), any(), any(), any());
    }

    @Test
    void dayOutsideTheWeekIsRefused() {
        var failure = assertThrows(ResponseStatusException.class,
                () -> service.addItem(planId, new AddWeeklyPlanItemRequest(LocalDate.now().plusDays(20))));

        assertEquals(HttpStatus.BAD_REQUEST, failure.getStatusCode());
    }

    @Test
    void dayWhoseTimeAlreadyPassedIsRefused() {
        plan.setStartDate(LocalDate.now().minusDays(2));
        plan.setEndDate(LocalDate.now().plusDays(4));

        var failure = assertThrows(ResponseStatusException.class,
                () -> service.addItem(planId, new AddWeeklyPlanItemRequest(LocalDate.now().minusDays(1))));

        assertEquals(HttpStatus.BAD_REQUEST, failure.getStatusCode());
        verify(generator, never()).addDay(any(), any(), any(), any(), any());
    }

    @Test
    void noAiUnitsLeftMeansNoNewDay() {
        when(usage.summary(userId)).thenReturn(units(0));

        var failure = assertThrows(ResponseStatusException.class,
                () -> service.addItem(planId, new AddWeeklyPlanItemRequest(LocalDate.now().plusDays(3))));

        assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
        verify(generator, never()).addDay(any(), any(), any(), any(), any());
    }

    @Test
    void approvedWeekCannotGetNewDays() {
        plan.setStatus(ContentCalendarStatus.APPROVED);

        var failure = assertThrows(ResponseStatusException.class,
                () -> service.addItem(planId, new AddWeeklyPlanItemRequest(LocalDate.now().plusDays(3))));

        assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
    }
}
