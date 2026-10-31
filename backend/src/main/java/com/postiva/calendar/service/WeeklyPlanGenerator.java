package com.postiva.calendar.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.postiva.ai.dto.WeeklyIdeasResult;
import com.postiva.ai.dto.WeeklyPlanPromptInput;
import com.postiva.ai.service.OpenAiService;
import com.postiva.auth.security.PersistentRateLimiter;
import com.postiva.business.entity.BusinessProfile;
import com.postiva.business.service.ShopProfileService;
import com.postiva.calendar.dto.CreateWeeklyPlanRequest;
import com.postiva.calendar.entity.CalendarItem;
import com.postiva.calendar.entity.CalendarItemStatus;
import com.postiva.calendar.entity.ContentCalendar;
import com.postiva.calendar.entity.ContentCalendarStatus;
import com.postiva.calendar.repository.CalendarItemRepository;
import com.postiva.calendar.repository.ContentCalendarRepository;
import com.postiva.common.Platform;
import com.postiva.common.RateLimitException;
import com.postiva.config.OpenAiProperties;
import com.postiva.post.dto.GeneratePostRequest;
import com.postiva.post.service.PostService;
import com.postiva.social.entity.SocialAccount;
import com.postiva.social.insights.PerformanceAnalyzer;
import com.postiva.social.insights.PerformanceSummary;
import com.postiva.social.insights.PostInsightsService;
import com.postiva.social.service.SocialAccountService;
import com.postiva.template.entity.IndustryTemplate;
import com.postiva.template.service.TemplateService;
import com.postiva.usage.AiUsageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Supplier;

/**
 * Builds a weekly plan in the background: read past posts, analyse them, ask the AI for one idea per day, then
 * write each day's draft through the normal post generator so quota, cost control and media handling stay the same.
 * Nothing here publishes; the shop approves the finished week in {@link WeeklyPlanService#approve}.
 */
@Service
public class WeeklyPlanGenerator {
    static final String DEFAULT_TONE = "Thân thiện, gần gũi";
    private static final Logger log = LoggerFactory.getLogger(WeeklyPlanGenerator.class);
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };
    private static final String[] WEEKDAY_LABELS = {"", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ nhật"};
    /** Another AI request of the same user (manual post creation) blocks ours; wait for it this many times. */
    private static final int AI_SLOT_ATTEMPTS = 12;
    private static final long AI_SLOT_WAIT_MILLIS = 5_000;

    private final ContentCalendarRepository plans;
    private final CalendarItemRepository items;
    private final SocialAccountService accounts;
    private final PostInsightsService insights;
    private final PerformanceAnalyzer analyzer;
    private final OpenAiService ai;
    private final OpenAiProperties aiProperties;
    private final AiUsageService usage;
    private final PostService posts;
    private final TemplateService templates;
    private final ShopProfileService shopProfiles;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;
    private final Executor executor;

    public WeeklyPlanGenerator(ContentCalendarRepository plans, CalendarItemRepository items,
                               SocialAccountService accounts, PostInsightsService insights,
                               PerformanceAnalyzer analyzer, OpenAiService ai, OpenAiProperties aiProperties,
                               AiUsageService usage, PostService posts, TemplateService templates, ObjectMapper json,
                               ShopProfileService shopProfiles,
                               PlatformTransactionManager transactionManager,
                               @Qualifier("weeklyPlanExecutor") Executor executor) {
        this.plans = plans;
        this.items = items;
        this.accounts = accounts;
        this.insights = insights;
        this.analyzer = analyzer;
        this.ai = ai;
        this.aiProperties = aiProperties;
        this.usage = usage;
        this.posts = posts;
        this.templates = templates;
        this.shopProfiles = shopProfiles;
        this.json = json;
        this.transactions = new TransactionTemplate(transactionManager);
        this.executor = executor;
    }

    public void start(UUID planId) {
        try {
            executor.execute(() -> run(planId));
        } catch (RejectedExecutionException exception) {
            markFailed(planId, "Hệ thống đang bận lên kế hoạch cho nhiều shop. Vui lòng thử lại sau ít phút.");
        }
    }

    void run(UUID planId) {
        ContentCalendar plan = plans.findById(planId).orElse(null);
        if (plan == null || plan.getStatus() != ContentCalendarStatus.GENERATING) {
            return;
        }
        try {
            CreateWeeklyPlanRequest brief = json.convertValue(plan.getBrief(), CreateWeeklyPlanRequest.class);
            UUID userId = plan.getUserId();
            ZoneId zone = ZoneId.of(plan.getTimezone());

            SocialAccount account = accounts.prepareForPublishing(plan.getSocialAccountId());
            if (!account.getUserId().equals(userId)) {
                throw new IllegalStateException("Social account does not belong to the plan owner");
            }
            var recent = insights.recentPosts(account, accounts.decryptToken(account));
            PerformanceSummary summary = analyzer.analyze(recent.posts(), recent.warning(), zone);
            plan.setPerformance(json.convertValue(summary, MAP));
            save(plan);

            LocalTime time = brief.postTime() != null ? LocalTime.parse(brief.postTime())
                    : summary.bestHour() != null ? LocalTime.of(summary.bestHour(), 0) : WeeklySchedule.DEFAULT_TIME;
            List<Instant> slots = WeeklySchedule.pick(plan.getStartDate(), brief.postsPerWeek(), brief.daysOfWeek(),
                    summary.bestWeekdays(), time, zone, Instant.now());
            if (slots.isEmpty()) {
                markFailed(planId, "Tuần đã chọn không còn ngày nào để đăng. Hãy chọn tuần bắt đầu muộn hơn.");
                return;
            }

            WeeklyIdeasResult ideas = planIdeas(userId, brief, account.getProvider(), slots, summary, zone);
            plan.setStrategy(ideas.strategy().isBlank() ? null : ideas.strategy());
            List<CalendarItem> created = createItems(plan, ideas, slots, account.getProvider(), zone, brief.postTime() != null);
            save(plan);

            int failed = draftAll(plan, brief, account.getProvider(), created, summary, zone);
            plan.setStatus(ContentCalendarStatus.READY);
            plan.setError(failed == 0 ? null : failed + " bài chưa soạn được. Bạn vẫn có thể duyệt các bài còn lại.");
            save(plan);
        } catch (RuntimeException exception) {
            log.warn("Weekly plan {} failed: {}", planId, exception.getMessage());
            markFailed(planId, userMessage(exception, "Không lên được kế hoạch tuần. Vui lòng thử lại."));
        }
    }

    /** Post types offered, in order, to a day the shop adds by hand; the first one the week has not used yet wins. */
    private static final List<String> ADDED_DAY_TYPES = List.of(
            "Giới thiệu sản phẩm", "Feedback khách hàng", "Ưu đãi", "Hậu trường", "Mẹo sử dụng");

    /**
     * Writes one more day into a plan that is ready for review. The draft goes through the normal post generator, so
     * it costs one AI unit and carries the week's images. If the draft cannot be written nothing is left behind.
     */
    public void addDay(ContentCalendar plan, LocalDate day, Instant slot, Platform platform, List<CalendarItem> existing) {
        CreateWeeklyPlanRequest brief = json.convertValue(plan.getBrief(), CreateWeeklyPlanRequest.class);
        ZoneId zone = ZoneId.of(plan.getTimezone());
        PerformanceSummary summary = plan.getPerformance() == null
                ? analyzer.analyze(List.of(), null, zone)
                : json.convertValue(plan.getPerformance(), PerformanceSummary.class);
        String postType = ADDED_DAY_TYPES.stream()
                .filter(type -> existing.stream().noneMatch(entry -> type.equals(entry.getPostType())))
                .findFirst().orElse("Bài đăng");

        CalendarItem item = new CalendarItem();
        item.setCalendarId(plan.getId());
        item.setPlatform(platform);
        item.setPlannedDate(day);
        item.setScheduledTime(slot);
        item.setPostType(postType);
        item.setTopic(postType + " — " + brief.productName());
        item.setReason("Bạn thêm ngày này vào kế hoạch.");
        item.setStatus(CalendarItemStatus.GENERATING);
        item = items.save(item);
        try {
            GeneratePostRequest request = draftRequest(brief, platform, item, summary, zone);
            var post = withAiSlot(plan.getUserId(),
                    () -> posts.generateForUser(plan.getUserId(), request, UUID.randomUUID()));
            item.setGeneratedPostId(post.postId());
            item.setStatus(CalendarItemStatus.DRAFT);
            items.save(item);
        } catch (RuntimeException exception) {
            log.warn("Added day for weekly plan {} failed: {}", plan.getId(), exception.getMessage());
            items.delete(item);
            throw exception;
        }
    }

    private WeeklyIdeasResult planIdeas(UUID userId, CreateWeeklyPlanRequest brief, Platform platform,
                                        List<Instant> slots, PerformanceSummary summary, ZoneId zone) {
        BusinessProfile profile = shopProfiles.forUser(userId).orElse(null);
        String industryCode = brief.industryCode() != null && !brief.industryCode().isBlank()
                ? brief.industryCode() : profile == null ? null : ShopProfileService.templateCode(profile);
        IndustryTemplate template = industryCode == null ? null : templates.findByCode(industryCode).orElse(null);
        List<String> slotLabels = slots.stream().map(slot -> slotLabel(slot, zone)).toList();
        WeeklyPlanPromptInput input = new WeeklyPlanPromptInput(
                brief.productName(), brief.productDescription(), brief.price(), brief.industryCode(),
                brief.targetAudience(), brief.highlights(), brief.goal(), toneOf(brief), platform, slotLabels,
                summary.insights(),
                summary.topPosts().stream().filter(post -> !post.excerpt().isBlank())
                        .map(post -> post.excerpt() + " (" + Math.round(post.engagement()) + " điểm tương tác)").toList(),
                template == null ? List.of() : template.getPostTypes(),
                template == null ? List.of() : template.getContentAngles(),
                profile == null ? null : shopProfiles.context(profile));

        String fingerprint;
        try {
            fingerprint = PersistentRateLimiter.sha256(json.writeValueAsString(input));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not fingerprint weekly plan prompt", exception);
        }
        var reservation = withAiSlot(userId, () -> usage.reserve(userId, UUID.randomUUID(), fingerprint, aiProperties.model()));
        try {
            WeeklyIdeasResult result = ai.generateWeeklyIdeas(input,
                    providerUsage -> usage.recordProviderUsage(reservation.requestId(), providerUsage));
            // The plan step produces ideas, not a post, so the usage row has no post id.
            transactions.executeWithoutResult(status -> usage.succeed(userId, reservation.requestId(), null));
            return result;
        } catch (RuntimeException exception) {
            boolean uncertain = !(exception instanceof ResponseStatusException status && status.getStatusCode().is4xxClientError());
            try {
                usage.fail(userId, reservation.requestId(), uncertain, "WEEKLY_PLAN_FAILED");
            } catch (RuntimeException failure) {
                exception.addSuppressed(failure);
            }
            throw exception;
        }
    }

    private List<CalendarItem> createItems(ContentCalendar plan, WeeklyIdeasResult ideas, List<Instant> slots,
                                           Platform platform, ZoneId zone, boolean fixedTime) {
        List<CalendarItem> created = new ArrayList<>();
        for (int index = 0; index < slots.size(); index++) {
            Instant slot = slots.get(index);
            LocalDate day = slot.atZone(zone).toLocalDate();
            WeeklyIdeasResult.Idea idea = ideaFor(ideas.items(), day, index);

            CalendarItem item = new CalendarItem();
            item.setCalendarId(plan.getId());
            item.setPlatform(platform);
            item.setPlannedDate(day);
            item.setScheduledTime(fixedTime ? slot : suggestedTime(idea, day, zone, slot));
            if (idea == null) {
                item.setPostType("Giới thiệu sản phẩm");
                item.setTopic("Giới thiệu " + plan.getBrief().getOrDefault("productName", "sản phẩm"));
                item.setReason("AI chưa đề xuất ý tưởng cho ngày này nên Postiva dùng bài giới thiệu sản phẩm.");
            } else {
                item.setPostType(idea.postType().isBlank() ? "Bài đăng" : idea.postType());
                item.setTopic(idea.angle().isBlank() ? idea.topic() : idea.topic() + " — " + idea.angle());
                item.setReason(idea.reason().isBlank() ? null : idea.reason());
            }
            item.setStatus(CalendarItemStatus.PLANNED);
            created.add(items.save(item));
        }
        return created;
    }

    /** Writes each day's draft. Returns how many could not be written. */
    private int draftAll(ContentCalendar plan, CreateWeeklyPlanRequest brief, Platform platform,
                         List<CalendarItem> created, PerformanceSummary summary, ZoneId zone) {
        int failed = 0;
        String stopReason = null;
        for (CalendarItem item : created) {
            if (stopReason != null) {
                failItem(item, stopReason);
                failed++;
                continue;
            }
            item.setStatus(CalendarItemStatus.GENERATING);
            items.save(item);
            try {
                GeneratePostRequest request = draftRequest(brief, platform, item, summary, zone);
                var post = withAiSlot(plan.getUserId(),
                        () -> posts.generateForUser(plan.getUserId(), request, UUID.randomUUID()));
                item.setGeneratedPostId(post.postId());
                item.setError(null);
                item.setStatus(CalendarItemStatus.DRAFT);
                items.save(item);
            } catch (RateLimitException exception) {
                // Quota or daily limit reached: every remaining draft would fail the same way.
                stopReason = userMessage(exception, "Đã hết lượt AI.");
                failItem(item, stopReason);
                failed++;
            } catch (RuntimeException exception) {
                log.warn("Draft for weekly plan item {} failed: {}", item.getId(), exception.getMessage());
                failItem(item, userMessage(exception, "Không soạn được bài này."));
                failed++;
            }
            plan.touch();
            save(plan);
        }
        return failed;
    }

    private GeneratePostRequest draftRequest(CreateWeeklyPlanRequest brief, Platform platform, CalendarItem item,
                                             PerformanceSummary summary, ZoneId zone) {
        StringBuilder info = new StringBuilder()
                .append("Bài trong kế hoạch tuần, đăng ").append(slotLabel(item.getScheduledTime(), zone)).append(".\n")
                .append("Loại bài: ").append(item.getPostType()).append(".\n")
                .append("Chủ đề và cách tiếp cận: ").append(item.getTopic()).append(".\n");
        if (item.getReason() != null) {
            info.append("Mục đích: ").append(item.getReason()).append("\n");
        }
        if (!summary.insights().isEmpty()) {
            info.append("Rút ra từ bài cũ của shop: ").append(String.join(" ", summary.insights())).append("\n");
        }
        return new GeneratePostRequest(
                brief.productName(), brief.productDescription(), brief.price(), brief.industryCode(),
                brief.targetAudience(), brief.highlights(),
                truncate("Mục tiêu tuần: " + brief.goal() + ". Bài này: " + item.getTopic(), 1000),
                toneOf(brief), List.of(platform), truncate(info.toString(), 4000),
                brief.imageIds() == null ? List.of() : brief.imageIds());
    }

    /** Retries while another AI request of the same user is running; any other limit is final. */
    private <T> T withAiSlot(UUID userId, Supplier<T> action) {
        for (int attempt = 1; ; attempt++) {
            try {
                return action.get();
            } catch (RateLimitException exception) {
                if (attempt >= AI_SLOT_ATTEMPTS || usage.summary(userId).reserved() == 0) {
                    throw exception;
                }
                try {
                    Thread.sleep(AI_SLOT_WAIT_MILLIS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw exception;
                }
            }
        }
    }

    private static WeeklyIdeasResult.Idea ideaFor(List<WeeklyIdeasResult.Idea> ideas, LocalDate day, int index) {
        String date = day.toString();
        return ideas.stream().filter(idea -> date.equals(idea.date())).findFirst()
                .orElse(index < ideas.size() ? ideas.get(index) : null);
    }

    /** The AI may move a post within the same day; anything unusable keeps the computed slot. */
    private static Instant suggestedTime(WeeklyIdeasResult.Idea idea, LocalDate day, ZoneId zone, Instant fallback) {
        if (idea == null || idea.time() == null || !idea.time().matches("^([01]\\d|2[0-3]):[0-5]\\d$")) {
            return fallback;
        }
        LocalTime time = LocalTime.parse(idea.time());
        Instant suggested = day.atTime(time).atZone(zone).toInstant();
        boolean reasonableHour = !time.isBefore(LocalTime.of(6, 0));
        return reasonableHour && suggested.isAfter(Instant.now().plus(WeeklySchedule.MIN_LEAD)) ? suggested : fallback;
    }

    private void failItem(CalendarItem item, String message) {
        item.setError(message);
        item.setStatus(CalendarItemStatus.FAILED);
        items.save(item);
    }

    private void markFailed(UUID planId, String message) {
        plans.findById(planId).ifPresent(plan -> {
            plan.setStatus(ContentCalendarStatus.FAILED);
            plan.setError(message);
            save(plan);
        });
    }

    private void save(ContentCalendar plan) {
        plan.touch();
        plans.save(plan);
    }

    static String slotLabel(Instant slot, ZoneId zone) {
        ZonedDateTime local = slot.atZone(zone);
        return local.toLocalDate() + " (" + WEEKDAY_LABELS[local.getDayOfWeek().getValue()] + ") "
                + local.format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    private static String toneOf(CreateWeeklyPlanRequest brief) {
        return brief.tone() == null || brief.tone().isBlank() ? DEFAULT_TONE : brief.tone();
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    static String userMessage(RuntimeException exception, String fallback) {
        if (exception instanceof ResponseStatusException status && status.getReason() != null && !status.getReason().isBlank()) {
            return status.getReason();
        }
        return fallback;
    }
}
