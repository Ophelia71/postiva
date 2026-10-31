package com.postiva.calendar.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.postiva.auth.service.CurrentUserService;
import com.postiva.calendar.dto.AddWeeklyPlanItemRequest;
import com.postiva.calendar.dto.CreateWeeklyPlanRequest;
import com.postiva.calendar.dto.UpdateWeeklyPlanItemRequest;
import com.postiva.calendar.dto.WeeklyPlanResponse;
import com.postiva.calendar.entity.CalendarItem;
import com.postiva.calendar.entity.CalendarItemStatus;
import com.postiva.calendar.entity.ContentCalendar;
import com.postiva.calendar.entity.ContentCalendarStatus;
import com.postiva.calendar.repository.CalendarItemRepository;
import com.postiva.calendar.repository.ContentCalendarRepository;
import com.postiva.common.Platform;
import com.postiva.config.OpenAiProperties;
import com.postiva.file.service.FileService;
import com.postiva.post.dto.GeneratePostResponse;
import com.postiva.post.service.PostService;
import com.postiva.scheduler.dto.SchedulePostRequest;
import com.postiva.scheduler.entity.ScheduledPost;
import com.postiva.scheduler.repository.ScheduledPostRepository;
import com.postiva.scheduler.service.PublishingService;
import com.postiva.social.entity.SocialAccount;
import com.postiva.social.insights.PerformanceAnalyzer;
import com.postiva.social.insights.PerformanceSummary;
import com.postiva.social.insights.PostInsightsService;
import com.postiva.social.repository.SocialAccountRepository;
import com.postiva.social.service.SocialAccountService;
import com.postiva.usage.AiUsageService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Request-facing side of weekly plans; the slow work happens in {@link WeeklyPlanGenerator}. */
@Service
public class WeeklyPlanService {
    static final String DEFAULT_TIMEZONE = "Asia/Ho_Chi_Minh";
    /** How far ahead a week may start. Meta only schedules Page posts up to 30 days out. */
    static final int MAX_DAYS_AHEAD = 21;
    /** A plan still GENERATING after this long lost its worker (restart, crash) and is reported as failed. */
    static final Duration STALE_AFTER = Duration.ofMinutes(20);
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };

    private final ContentCalendarRepository plans;
    private final CalendarItemRepository items;
    private final WeeklyPlanGenerator generator;
    private final CurrentUserService currentUser;
    private final SocialAccountService accounts;
    private final SocialAccountRepository accountRepository;
    private final PostService posts;
    private final PublishingService publishing;
    private final ScheduledPostRepository scheduledPosts;
    private final FileService files;
    private final AiUsageService usage;
    private final OpenAiProperties aiProperties;
    private final PostInsightsService insights;
    private final PerformanceAnalyzer analyzer;
    private final ObjectMapper json;

    public WeeklyPlanService(ContentCalendarRepository plans, CalendarItemRepository items,
                             WeeklyPlanGenerator generator, CurrentUserService currentUser,
                             SocialAccountService accounts, SocialAccountRepository accountRepository,
                             PostService posts, PublishingService publishing, ScheduledPostRepository scheduledPosts,
                             FileService files, AiUsageService usage, OpenAiProperties aiProperties,
                             PostInsightsService insights, PerformanceAnalyzer analyzer, ObjectMapper json) {
        this.plans = plans;
        this.items = items;
        this.generator = generator;
        this.currentUser = currentUser;
        this.accounts = accounts;
        this.accountRepository = accountRepository;
        this.posts = posts;
        this.publishing = publishing;
        this.scheduledPosts = scheduledPosts;
        this.files = files;
        this.usage = usage;
        this.aiProperties = aiProperties;
        this.insights = insights;
        this.analyzer = analyzer;
        this.json = json;
    }

    /** AI units a plan of {@code postsPerWeek} posts uses: one for the ideas plus one per draft. */
    public static int aiUnitsFor(int postsPerWeek) {
        return postsPerWeek + 1;
    }

    public WeeklyPlanResponse create(CreateWeeklyPlanRequest request) {
        UUID userId = currentUser.requireCurrentUserId();
        SocialAccount account = accounts.requireCurrentUserAccount(request.socialAccountId());
        ZoneId zone = zone(request.timezone());
        LocalDate today = LocalDate.now(zone);
        if (request.startDate().isBefore(today) || request.startDate().isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            throw error(HttpStatus.BAD_REQUEST, "Tuần lên kế hoạch phải bắt đầu từ hôm nay đến " + MAX_DAYS_AHEAD + " ngày tới");
        }
        if (!aiProperties.enabled()) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "Dịch vụ AI chưa sẵn sàng");
        }
        if (account.getProvider() == Platform.INSTAGRAM && (request.imageIds() == null || request.imageIds().isEmpty())) {
            throw error(HttpStatus.BAD_REQUEST, "Instagram cần ít nhất một ảnh hoặc video cho các bài trong tuần");
        }
        plans.findTop10ByUserIdAndSocialAccountIdIsNotNullOrderByCreatedAtDesc(userId).forEach(this::recoverIfStale);
        if (plans.existsByUserIdAndStatus(userId, ContentCalendarStatus.GENERATING)) {
            throw error(HttpStatus.CONFLICT, "Postiva đang lên một kế hoạch khác cho bạn. Vui lòng chờ kế hoạch đó xong.");
        }
        files.validateAttachments(request.imageIds(), userId);
        int needed = aiUnitsFor(request.postsPerWeek());
        int remaining = usage.summary(userId).remaining();
        if (remaining < needed) {
            throw error(HttpStatus.CONFLICT, "Kế hoạch " + request.postsPerWeek() + " bài cần " + needed
                    + " lượt AI, bạn còn " + remaining + " lượt. Hãy giảm số bài hoặc nâng cấp gói.");
        }

        ContentCalendar plan = new ContentCalendar();
        plan.setUserId(userId);
        plan.setSocialAccountId(account.getId());
        plan.setIndustryCode(request.industryCode());
        plan.setGoal(request.goal().trim());
        plan.setStartDate(request.startDate());
        plan.setEndDate(request.startDate().plusDays(6));
        plan.setTimezone(zone.getId());
        plan.setStatus(ContentCalendarStatus.GENERATING);
        plan.setBrief(json.convertValue(request, MAP));
        // Saved (and committed) before the worker starts, so it always finds the row.
        plan = plans.save(plan);
        generator.start(plan.getId());
        return response(plan, userId);
    }

    public List<WeeklyPlanResponse> list() {
        UUID userId = currentUser.requireCurrentUserId();
        return plans.findTop10ByUserIdAndSocialAccountIdIsNotNullOrderByCreatedAtDesc(userId).stream()
                .map(this::recoverIfStale)
                .map(plan -> response(plan, userId))
                .toList();
    }

    public WeeklyPlanResponse get(UUID planId) {
        UUID userId = currentUser.requireCurrentUserId();
        return response(recoverIfStale(requirePlan(planId, userId)), userId);
    }

    public WeeklyPlanResponse updateItem(UUID planId, UUID itemId, UpdateWeeklyPlanItemRequest request) {
        UUID userId = currentUser.requireCurrentUserId();
        ContentCalendar plan = requireReady(planId, userId);
        CalendarItem item = items.findByIdAndCalendarId(itemId, planId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Không tìm thấy bài trong kế hoạch"));
        if (request.scheduledTime() != null) {
            Instant earliest = Instant.now().plus(WeeklySchedule.MIN_LEAD);
            if (request.scheduledTime().isBefore(earliest) || request.scheduledTime().isAfter(Instant.now().plus(Duration.ofDays(30)))) {
                throw error(HttpStatus.BAD_REQUEST, "Giờ đăng phải sau ít nhất 30 phút và trong vòng 30 ngày tới");
            }
            item.setScheduledTime(request.scheduledTime());
            item.setPlannedDate(request.scheduledTime().atZone(ZoneId.of(plan.getTimezone())).toLocalDate());
        }
        if (Boolean.TRUE.equals(request.skipped())) {
            if (item.getStatus() != CalendarItemStatus.DRAFT && item.getStatus() != CalendarItemStatus.FAILED) {
                throw error(HttpStatus.CONFLICT, "Chỉ bỏ qua được bài chưa lên lịch");
            }
            item.setStatus(CalendarItemStatus.SKIPPED);
        } else if (Boolean.FALSE.equals(request.skipped()) && item.getStatus() == CalendarItemStatus.SKIPPED) {
            item.setStatus(item.getGeneratedPostId() == null ? CalendarItemStatus.FAILED : CalendarItemStatus.DRAFT);
        }
        items.save(item);
        plan.touch();
        plans.save(plan);
        return response(plan, userId);
    }

    /** Adds a post on a day of the week that has none, written now with one AI unit. */
    public WeeklyPlanResponse addItem(UUID planId, AddWeeklyPlanItemRequest request) {
        UUID userId = currentUser.requireCurrentUserId();
        ContentCalendar plan = requireReady(planId, userId);
        ZoneId zone = ZoneId.of(plan.getTimezone());
        LocalDate day = request.plannedDate();
        if (day.isBefore(plan.getStartDate()) || day.isAfter(plan.getEndDate())) {
            throw error(HttpStatus.BAD_REQUEST, "Ngày này nằm ngoài tuần của kế hoạch");
        }
        List<CalendarItem> existing = items.findByCalendarIdOrderByPlannedDateAsc(plan.getId());
        if (existing.stream().anyMatch(item -> day.equals(item.getPlannedDate()))) {
            throw error(HttpStatus.CONFLICT, "Ngày này đã có bài trong kế hoạch");
        }
        CreateWeeklyPlanRequest brief = json.convertValue(plan.getBrief(), CreateWeeklyPlanRequest.class);
        LocalTime time = brief.postTime() != null ? LocalTime.parse(brief.postTime())
                : plan.getPerformance() != null && plan.getPerformance().get("bestHour") instanceof Number hour
                ? LocalTime.of(hour.intValue(), 0) : WeeklySchedule.DEFAULT_TIME;
        Instant slot = day.atTime(time).atZone(zone).toInstant();
        if (!slot.isAfter(Instant.now().plus(WeeklySchedule.MIN_LEAD))) {
            throw error(HttpStatus.BAD_REQUEST, "Giờ đăng của ngày này đã qua hoặc còn dưới 30 phút. Hãy chọn ngày khác.");
        }
        if (!aiProperties.enabled()) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "Dịch vụ AI chưa sẵn sàng");
        }
        if (usage.summary(userId).remaining() < 1) {
            throw error(HttpStatus.CONFLICT, "Bạn đã hết lượt AI. Hãy nâng cấp gói hoặc chờ lượt mới.");
        }
        SocialAccount account = accounts.requireCurrentUserAccount(plan.getSocialAccountId());
        generator.addDay(plan, day, slot, account.getProvider(), existing);
        plan.touch();
        plans.save(plan);
        return response(plan, userId);
    }

    /** Hands every reviewed draft to the existing scheduler. Days already in the past are skipped, not posted late. */
    public WeeklyPlanResponse approve(UUID planId) {
        UUID userId = currentUser.requireCurrentUserId();
        ContentCalendar plan = requireReady(planId, userId);
        Instant earliest = Instant.now().plus(Duration.ofMinutes(2));
        int scheduled = 0;
        for (CalendarItem item : items.findByCalendarIdOrderByPlannedDateAsc(planId)) {
            if (item.getStatus() != CalendarItemStatus.DRAFT || item.getGeneratedPostId() == null) {
                continue;
            }
            if (item.getScheduledTime() == null || item.getScheduledTime().isBefore(earliest)) {
                item.setError("Đã quá giờ đăng nên bài này được bỏ qua.");
                item.setStatus(CalendarItemStatus.SKIPPED);
                items.save(item);
                continue;
            }
            try {
                var result = publishing.schedule(new SchedulePostRequest(
                        item.getGeneratedPostId(), plan.getSocialAccountId(), item.getPlatform(), item.getScheduledTime()));
                item.setScheduledPostId(result.id());
                item.setError(null);
                item.setStatus(CalendarItemStatus.SCHEDULED);
                scheduled++;
            } catch (ResponseStatusException exception) {
                item.setError(WeeklyPlanGenerator.userMessage(exception, "Không lên lịch được bài này."));
                item.setStatus(CalendarItemStatus.FAILED);
            }
            items.save(item);
        }
        if (scheduled == 0) {
            throw error(HttpStatus.CONFLICT, "Không có bài nào lên lịch được. Hãy kiểm tra giờ đăng và kết nối kênh.");
        }
        plan.setStatus(ContentCalendarStatus.APPROVED);
        plan.touch();
        plans.save(plan);
        return response(plan, userId);
    }

    public void delete(UUID planId) {
        UUID userId = currentUser.requireCurrentUserId();
        ContentCalendar plan = recoverIfStale(requirePlan(planId, userId));
        if (plan.getStatus() == ContentCalendarStatus.GENERATING) {
            throw error(HttpStatus.CONFLICT, "Kế hoạch đang được soạn, hãy chờ xong rồi xóa");
        }
        if (plan.getStatus() == ContentCalendarStatus.APPROVED) {
            throw error(HttpStatus.CONFLICT, "Kế hoạch đã duyệt. Hãy hủy từng bài ở Lịch đăng nếu không muốn đăng nữa.");
        }
        // Drafts stay in the shop's posts; only the plan goes away.
        items.deleteAll(items.findByCalendarIdOrderByPlannedDateAsc(planId));
        plans.delete(plan);
    }

    /** Live analysis of an account's past posts, so the shop can see the findings before spending AI quota. */
    public PerformanceSummary insights(UUID socialAccountId, String timezone) {
        accounts.requireCurrentUserAccount(socialAccountId);
        SocialAccount account = accounts.prepareForPublishing(socialAccountId);
        var recent = insights.recentPosts(account, accounts.decryptToken(account));
        return analyzer.analyze(recent.posts(), recent.warning(), zone(timezone));
    }

    private ContentCalendar requirePlan(UUID planId, UUID userId) {
        return plans.findByIdAndUserId(planId, userId)
                .filter(plan -> plan.getSocialAccountId() != null)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Không tìm thấy kế hoạch tuần"));
    }

    private ContentCalendar requireReady(UUID planId, UUID userId) {
        ContentCalendar plan = recoverIfStale(requirePlan(planId, userId));
        if (plan.getStatus() != ContentCalendarStatus.READY) {
            throw error(HttpStatus.CONFLICT, switch (plan.getStatus()) {
                case GENERATING -> "Kế hoạch đang được soạn, hãy chờ xong";
                case APPROVED -> "Kế hoạch đã được duyệt";
                default -> "Kế hoạch này không còn chỉnh sửa được";
            });
        }
        return plan;
    }

    private ContentCalendar recoverIfStale(ContentCalendar plan) {
        if (plan.getStatus() == ContentCalendarStatus.GENERATING
                && plan.getUpdatedAt().isBefore(LocalDateTime.now().minus(STALE_AFTER))) {
            plan.setStatus(ContentCalendarStatus.FAILED);
            plan.setError("Quá trình lên kế hoạch bị gián đoạn. Hãy tạo lại kế hoạch.");
            plan.touch();
            return plans.save(plan);
        }
        return plan;
    }

    private WeeklyPlanResponse response(ContentCalendar plan, UUID userId) {
        List<CalendarItem> planItems = plan.getId() == null ? List.of() : items.findByCalendarIdOrderByPlannedDateAsc(plan.getId());
        Map<UUID, GeneratePostResponse> drafts = posts.getPostResponses(userId,
                planItems.stream().map(CalendarItem::getGeneratedPostId).filter(Objects::nonNull).toList());
        Map<UUID, ScheduledPost> schedules = scheduledPosts.findAllById(
                        planItems.stream().map(CalendarItem::getScheduledPostId).filter(Objects::nonNull).toList())
                .stream().collect(Collectors.toMap(ScheduledPost::getId, Function.identity()));
        SocialAccount account = plan.getSocialAccountId() == null ? null
                : accountRepository.findById(plan.getSocialAccountId()).orElse(null);

        List<WeeklyPlanResponse.Item> responseItems = planItems.stream().map(item -> {
            ScheduledPost schedule = item.getScheduledPostId() == null ? null : schedules.get(item.getScheduledPostId());
            return new WeeklyPlanResponse.Item(item.getId(), item.getPlannedDate(), item.getScheduledTime(),
                    item.getPostType(), item.getTopic(), item.getReason(), itemStatus(item, schedule),
                    schedule != null && schedule.getLastError() != null ? schedule.getLastError() : item.getError(),
                    item.getScheduledPostId(),
                    item.getGeneratedPostId() == null ? null : drafts.get(item.getGeneratedPostId()),
                    item.getGeneratedPostId() == null ? List.of() : files.allMediaForPost(item.getGeneratedPostId()).stream()
                            .map(file -> new WeeklyPlanResponse.Media(file.getId(), "/api/files/" + file.getId(),
                                    file.getMimeType(), file.getFileName()))
                            .toList());
        }).toList();

        return new WeeklyPlanResponse(plan.getId(), plan.getStatus().name(), plan.getSocialAccountId(),
                account == null ? null : account.getProvider().name(),
                account == null ? null : account.getPageName(),
                plan.getStartDate(), plan.getEndDate(), plan.getGoal(), plan.getTimezone(), plan.getStrategy(),
                plan.getError(), plan.getPerformance(), responseItems, plan.getCreatedAt(), plan.getUpdatedAt());
    }

    /** After approval the scheduler owns the post, so its state wins over the plan's copy. */
    private static String itemStatus(CalendarItem item, ScheduledPost schedule) {
        if (schedule == null) {
            return item.getStatus().name();
        }
        return switch (schedule.getStatus()) {
            case PUBLISHED -> CalendarItemStatus.PUBLISHED.name();
            case FAILED, UNKNOWN -> CalendarItemStatus.FAILED.name();
            case CANCELLED -> CalendarItemStatus.SKIPPED.name();
            case SCHEDULED, PUBLISHING -> CalendarItemStatus.SCHEDULED.name();
        };
    }

    private static ZoneId zone(String timezone) {
        try {
            return ZoneId.of(timezone == null || timezone.isBlank() ? DEFAULT_TIMEZONE : timezone);
        } catch (DateTimeException exception) {
            throw error(HttpStatus.BAD_REQUEST, "Múi giờ không hợp lệ");
        }
    }

    private static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
