package com.postiva.automation;

import com.postiva.automation.AutomationDtos.Brief;
import com.postiva.automation.AutomationDtos.PreviewItem;
import com.postiva.automation.AutomationDtos.RuleRequest;
import com.postiva.automation.AutomationDtos.RuleResponse;
import com.postiva.automation.AutomationDtos.RunResponse;
import com.postiva.automation.AutomationStore.AccountState;
import com.postiva.automation.AutomationStore.LastPublish;
import com.postiva.automation.AutomationStore.Rule;
import com.postiva.automation.AutomationStore.Run;
import com.postiva.automation.AutomationStore.RunView;
import com.postiva.common.Platform;
import com.postiva.common.RateLimitException;
import com.postiva.post.dto.GeneratePostRequest;
import com.postiva.post.dto.GeneratePostResponse;
import com.postiva.post.service.PostService;
import com.postiva.scheduler.dto.SchedulePostRequest;
import com.postiva.scheduler.dto.ScheduledPostResponse;
import com.postiva.scheduler.service.PublishingService;
import com.postiva.social.entity.SocialAccount;
import com.postiva.social.service.SocialAccountService;
import com.postiva.usage.AiRequestConflictException;
import com.postiva.usage.AiUsageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AutomationService {
    private static final Logger log = LoggerFactory.getLogger(AutomationService.class);
    private static final String REVIEW = "REVIEW";
    private static final String AUTO = "AUTO";
    private static final String DEFAULT_GOAL = "Tăng tương tác và khuyến khích khách hàng nhắn tin đặt hàng";
    private static final String DEFAULT_TONE = "Thân thiện, tự nhiên, phù hợp bán hàng online";
    private static final List<String> ANGLES = List.of(
            "điểm nổi bật của sản phẩm", "câu hỏi khách hàng hay hỏi", "ưu đãi hoặc lý do nên đặt sớm",
            "câu chuyện hoặc cảm nhận của khách hàng");

    private final AutomationStore store;
    private final AutomationProperties properties;
    private final AutomationPauser pauser;
    private final PostService posts;
    private final PublishingService publishing;
    private final SocialAccountService accounts;
    private final AiUsageService usage;
    private final TransactionTemplate transactions;

    public AutomationService(AutomationStore store, AutomationProperties properties, AutomationPauser pauser,
                             PostService posts, PublishingService publishing, SocialAccountService accounts,
                             AiUsageService usage, PlatformTransactionManager transactionManager) {
        this.store = store;
        this.properties = properties;
        this.pauser = pauser;
        this.posts = posts;
        this.publishing = publishing;
        this.accounts = accounts;
        this.usage = usage;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public record Status(boolean enabled, boolean autoEnabled, int maxRulesPerUser, int manualQuotaReserve) { }

    public Status status() {
        return new Status(properties.isEnabled(), properties.isAutoEnabled(), properties.getMaxRulesPerUser(),
                properties.getManualQuotaReserve());
    }

    // ------------------------------------------------------------------ rules

    @Transactional
    public RuleResponse create(UUID userId, RuleRequest request) {
        requireEnabled();
        if (store.countRules(userId) >= properties.getMaxRulesPerUser()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Bạn đã đạt số rule tối đa (" + properties.getMaxRulesPerUser() + "). Hãy xóa rule không dùng.");
        }
        Parsed parsed = parse(userId, request);
        UUID id = store.insertRule(userId, request.socialAccountId(), Platform.FACEBOOK.name(), request.name().trim(),
                parsed.zone().getId(), parsed.time(), parsed.mask(), parsed.mode(), parsed.lead(), parsed.brief());
        return response(store.findRule(userId, id).orElseThrow());
    }

    @Transactional
    public RuleResponse update(UUID userId, UUID ruleId, RuleRequest request) {
        requireEnabled();
        findOwned(userId, ruleId);
        Parsed parsed = parse(userId, request);
        store.updateRule(userId, ruleId, request.socialAccountId(), request.name().trim(), parsed.zone().getId(),
                parsed.time(), parsed.mask(), parsed.mode(), parsed.lead(), parsed.brief());
        return response(findOwned(userId, ruleId));
    }

    @Transactional(readOnly = true)
    public List<RuleResponse> list(UUID userId) {
        requireEnabled();
        return store.listRules(userId).stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public RuleResponse get(UUID userId, UUID ruleId) {
        requireEnabled();
        return response(findOwned(userId, ruleId));
    }

    @Transactional
    public void delete(UUID userId, UUID ruleId) {
        requireEnabled();
        if (!store.deleteRule(userId, ruleId)) throw notFound("Không tìm thấy rule");
    }

    @Transactional
    public RuleResponse pause(UUID userId, UUID ruleId) {
        requireEnabled();
        findOwned(userId, ruleId);
        pauser.pauseRule(ruleId, AutomationPauser.REASON_USER);
        return response(findOwned(userId, ruleId));
    }

    /** Only the owner resumes a rule, and only when what paused it is fixed. */
    @Transactional
    public RuleResponse resume(UUID userId, UUID ruleId) {
        requireEnabled();
        Rule rule = findOwned(userId, ruleId);
        if (!"PAUSED".equals(rule.status())) return response(rule);
        Verdict verdict = check(rule);
        if (verdict.pauseReason() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, reasonMessage(verdict.pauseReason()));
        }
        store.resumeRule(userId, ruleId);
        return response(findOwned(userId, ruleId));
    }

    @Transactional(readOnly = true)
    public List<PreviewItem> preview(UUID userId, UUID ruleId) {
        requireEnabled();
        Rule rule = findOwned(userId, ruleId);
        return AutomationSchedule.upcoming(rule.daysMask(), rule.timeOfDay(), ZoneId.of(rule.timezone()), Instant.now(), 7)
                .stream().map(p -> new PreviewItem(p.runDate(), p.at())).toList();
    }

    // ------------------------------------------------------------------- runs

    @Transactional(readOnly = true)
    public List<RunResponse> runs(UUID userId, String status) {
        requireEnabled();
        return store.listRunViews(userId, status, 50).stream().map(this::response).toList();
    }

    /** Approving schedules the draft through the ordinary scheduler; it can happen at most once per run. */
    @Transactional
    public RunResponse approve(UUID userId, UUID runId) {
        requireEnabled();
        RunView existing = store.findRunView(userId, runId).orElseThrow(() -> notFound("Không tìm thấy bản nháp"));
        Run run = store.claimForScheduling(userId, runId).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.CONFLICT, "Bản nháp đã được xử lý hoặc đã quá giờ đăng"));
        Rule rule = store.findRuleById(run.ruleId()).orElseThrow(() -> notFound("Không tìm thấy rule"));
        if (run.postId() == null || rule.accountId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bản nháp không còn kênh để đăng");
        }
        ScheduledPostResponse scheduled = publishing.scheduleForUser(userId, new SchedulePostRequest(
                run.postId(), rule.accountId(), Platform.valueOf(rule.platform()), run.plannedFor()));
        store.setScheduledPost(run.id(), scheduled.id());
        return store.findRunView(userId, runId).map(this::response)
                .orElse(response(new RunView(run, existing.ruleName(), existing.draft())));
    }

    @Transactional
    public RunResponse skip(UUID userId, UUID runId) {
        requireEnabled();
        store.findRunView(userId, runId).orElseThrow(() -> notFound("Không tìm thấy bản nháp"));
        if (!store.skipRun(userId, runId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bản nháp đã được xử lý");
        }
        return response(store.findRunView(userId, runId).orElseThrow());
    }

    // ---------------------------------------------------------------- planner

    /**
     * One planner pass: expire stale work, retry interrupted generations, then prepare the posts of
     * every active rule whose slot falls inside its lead window. Safe to run on several instances.
     */
    public void planOnce(Instant now) {
        store.expireReview(now);
        store.skipExhaustedGenerations(now, properties.getMaxGenerationAttempts());
        UUID claim = UUID.randomUUID();
        Instant lease = now.plusSeconds(properties.getLeaseSeconds());
        for (Run run : store.claimDueGenerations(now, claim, lease, properties.getMaxGenerationAttempts(), 20)) {
            guarded(run, claim, now);
        }
        for (Rule rule : store.activeRules()) {
            try {
                planRule(rule, now);
            } catch (RuntimeException e) {
                log.error("Daily automation could not plan rule {}", rule.id(), e);
            }
        }
    }

    private void planRule(Rule rule, Instant now) {
        Verdict verdict = check(rule);
        if (verdict.pauseReason() != null) {
            pauser.pauseRule(rule.id(), verdict.pauseReason());
            return;
        }
        if (verdict.defer()) return;
        ZoneId zone = ZoneId.of(rule.timezone());
        for (AutomationSchedule.Planned planned : AutomationSchedule.dueWithin(
                rule.daysMask(), rule.timeOfDay(), zone, now, rule.leadMinutes())) {
            UUID claim = UUID.randomUUID();
            Instant lease = now.plusSeconds(properties.getLeaseSeconds());
            // The unique (rule, day) key decides which instance owns the day; everyone else gets nothing back.
            Optional<Run> created = store.tryCreateRun(rule.id(), planned.runDate(), planned.at(),
                    idempotencyKey(rule, planned.runDate()), claim, lease);
            created.ifPresent(run -> guarded(run, claim, now));
        }
    }

    private void guarded(Run run, UUID claim, Instant now) {
        try {
            process(run, claim, now);
        } catch (RuntimeException e) {
            log.error("Daily automation run {} failed", run.id(), e);
            store.finishRun(run.id(), claim, "FAILED", "GENERATION_FAILED");
        }
    }

    private void process(Run run, UUID claim, Instant now) {
        Rule rule = store.findRuleById(run.ruleId()).orElse(null);
        if (rule == null) return;
        if (!"ACTIVE".equals(rule.status())) {
            store.finishRun(run.id(), claim, "SKIPPED", "PAUSED");
            return;
        }
        Verdict verdict = check(rule);
        if (verdict.pauseReason() != null) {
            pauser.pauseRule(rule.id(), verdict.pauseReason());
            return;
        }
        if (usage.summary(rule.userId()).remaining() <= properties.getManualQuotaReserve()) {
            pauser.pauseRule(rule.id(), AutomationPauser.REASON_AI_QUOTA);
            return;
        }
        GeneratePostResponse generated;
        try {
            generated = posts.generateForUser(rule.userId(), request(rule, run), run.idempotencyKey());
        } catch (RateLimitException e) {
            if (e.limit() == RateLimitException.Reason.AI_PERIOD_QUOTA) {
                pauser.pauseRule(rule.id(), AutomationPauser.REASON_AI_QUOTA);
            } else {
                defer(run, claim, now, "AI_BUSY");
            }
            return;
        } catch (AiRequestConflictException e) {
            // An earlier attempt with this key ended without a post; never call the provider a second time.
            store.finishRun(run.id(), claim, "FAILED", "AI_UNCERTAIN");
            return;
        } catch (ResponseStatusException e) {
            int code = e.getStatusCode().value();
            if (code == 503) {
                defer(run, claim, now, "AI_UNAVAILABLE");
            } else if (code == 403) {
                pauser.pauseRule(rule.id(), AutomationPauser.REASON_USER_LOCKED);
            } else {
                store.finishRun(run.id(), claim, "FAILED", "GENERATION_FAILED");
            }
            return;
        }
        UUID postId = generated.postId();
        if (!store.attachPost(run.id(), claim, postId)) return;
        if (REVIEW.equals(rule.approvalMode()) || !properties.isAutoEnabled()) {
            // AUTO needs its own switch; without it the draft simply waits for review.
            store.markPendingReview(run.id(), claim, postId);
            return;
        }
        scheduleAutomatically(rule, run, claim, postId);
    }

    private void scheduleAutomatically(Rule rule, Run run, UUID claim, UUID postId) {
        try {
            transactions.executeWithoutResult(status -> {
                Run scheduled = store.claimOwnGenerationForScheduling(run.id(), claim)
                        .orElseThrow(() -> new IllegalStateException("Run is no longer owned"));
                ScheduledPostResponse response = publishing.scheduleForUser(rule.userId(), new SchedulePostRequest(
                        scheduled.postId(), rule.accountId(), Platform.valueOf(rule.platform()), scheduled.plannedFor()));
                store.setScheduledPost(scheduled.id(), response.id());
            });
        } catch (RuntimeException e) {
            log.warn("Daily automation could not schedule run {}", run.id(), e);
            // The transaction rolled back, so the run is still GENERATING under our claim: park it for review.
            store.markPendingReview(run.id(), claim, postId);
        }
    }

    private void defer(Run run, UUID claim, Instant now, String error) {
        store.releaseForRetry(run.id(), claim, now.plusSeconds(properties.getRetryDelaySeconds()), error);
    }

    private GeneratePostRequest request(Rule rule, Run run) {
        Brief brief = rule.brief();
        String angle = ANGLES.get((int) Math.floorMod(run.runDate().toEpochDay(), (long) ANGLES.size()));
        List<String> recent = store.recentTitles(rule.id(), run.runDate(), 3);
        String note = "Bài tự động cho ngày " + run.runDate() + ". Hôm nay hãy viết theo hướng: " + angle + "."
                + (recent.isEmpty() ? "" : " Không lặp lại các tiêu đề gần đây: " + String.join("; ", recent) + ".");
        return new GeneratePostRequest(brief.productName(), brief.productDescription(), brief.price(), null,
                brief.targetAudience(), brief.highlights(), orDefault(brief.goal(), DEFAULT_GOAL),
                orDefault(brief.tone(), DEFAULT_TONE), List.of(Platform.valueOf(rule.platform())), note, null);
    }

    static UUID idempotencyKey(Rule rule, LocalDate runDate) {
        return UUID.nameUUIDFromBytes((rule.id() + "|" + runDate + "|" + rule.version()).getBytes(StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------ pause logic

    /** {@code pauseReason} stops the rule; {@code defer} skips this pass only (a post is still being published). */
    record Verdict(String pauseReason, boolean defer) {
        static final Verdict OK = new Verdict(null, false);
    }

    Verdict check(Rule rule) {
        Optional<AccountState> account = store.accountState(rule.accountId());
        if (account.isEmpty() || account.get().disconnected()) {
            return new Verdict(AutomationPauser.REASON_CHANNEL_DISCONNECTED, false);
        }
        AccountState state = account.get();
        if (state.tokenMissing() || (state.tokenExpiresAt() != null && state.tokenExpiresAt().isBefore(LocalDateTime.now()))) {
            return new Verdict(AutomationPauser.REASON_TOKEN, false);
        }
        Optional<LastPublish> last = store.lastPublish(rule.id());
        if (last.isPresent()) {
            LastPublish publish = last.get();
            if ("UNKNOWN".equals(publish.status())) return new Verdict(AutomationPauser.REASON_UNKNOWN_RESULT, false);
            if ("FAILED".equals(publish.status()) && "TOKEN".equals(publish.failureCode())
                    && publish.updatedAt().isAfter(rule.updatedAt())) {
                return new Verdict(AutomationPauser.REASON_TOKEN, false);
            }
            if ("PUBLISHING".equals(publish.status())) return new Verdict(null, true);
        }
        return Verdict.OK;
    }

    static String reasonMessage(String reason) {
        return switch (reason) {
            case AutomationPauser.REASON_TOKEN -> "Token của kênh hết hạn hoặc thiếu quyền. Hãy kết nối lại kênh rồi bật lại rule.";
            case AutomationPauser.REASON_CHANNEL_DISCONNECTED -> "Kênh đã bị ngắt. Hãy kết nối lại và chọn kênh cho rule.";
            case AutomationPauser.REASON_UNKNOWN_RESULT -> "Bài gần nhất có kết quả chưa xác định. Hãy đối soát ở Lịch đăng trước khi bật lại.";
            case AutomationPauser.REASON_USER_LOCKED -> "Tài khoản đang bị khóa.";
            case AutomationPauser.REASON_AI_QUOTA -> "Đã hết lượt AI cần giữ cho dùng tay.";
            default -> "Rule đang tạm dừng.";
        };
    }

    // ----------------------------------------------------------------- helpers

    private record Parsed(ZoneId zone, LocalTime time, int mask, String mode, int lead, Brief brief) { }

    private Parsed parse(UUID userId, RuleRequest request) {
        ZoneId zone;
        try {
            if (!ZoneId.getAvailableZoneIds().contains(request.timezone().trim())) throw new DateTimeException("zone");
            zone = ZoneId.of(request.timezone().trim());
        } catch (DateTimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Múi giờ không hợp lệ");
        }
        LocalTime time = LocalTime.parse(request.timeOfDay());
        int mask;
        try {
            mask = AutomationSchedule.toMask(request.daysOfWeek());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        String mode = request.approvalMode().trim().toUpperCase();
        if (!REVIEW.equals(mode) && !AUTO.equals(mode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chế độ duyệt không hợp lệ");
        }
        if (AUTO.equals(mode) && !properties.isAutoEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chế độ tự động đăng chưa được mở. Hãy dùng chế độ duyệt trước.");
        }
        SocialAccount account = accounts.requireUserAccount(userId, request.socialAccountId());
        if (account.getProvider() != Platform.FACEBOOK) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Hiện Daily chỉ hỗ trợ Facebook Page");
        }
        Brief brief = request.brief();
        Brief clean = new Brief(brief.productName().trim(), brief.productDescription().trim(), blankToNull(brief.price()),
                brief.targetAudience().trim(), brief.highlights().trim(), orDefault(brief.goal(), DEFAULT_GOAL),
                orDefault(brief.tone(), DEFAULT_TONE));
        int lead = request.leadMinutes() == null ? 720 : request.leadMinutes();
        return new Parsed(zone, time, mask, mode, lead, clean);
    }

    private Rule findOwned(UUID userId, UUID ruleId) {
        return store.findRule(userId, ruleId).orElseThrow(() -> notFound("Không tìm thấy rule"));
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Daily chưa được bật.");
        }
    }

    private RuleResponse response(Rule rule) {
        String accountName = store.accountState(rule.accountId()).map(AccountState::pageName).orElse(null);
        return new RuleResponse(rule.id(), rule.accountId(), accountName, rule.platform(), rule.name(), rule.status(),
                rule.pausedReason(), rule.timezone(), rule.timeOfDay().toString(),
                AutomationSchedule.toDays(rule.daysMask()), rule.approvalMode(), rule.leadMinutes(), rule.brief(),
                rule.createdAt(), rule.updatedAt());
    }

    private RunResponse response(RunView view) {
        Run run = view.run();
        return new RunResponse(run.id(), run.ruleId(), view.ruleName(), run.runDate(), run.plannedFor(), run.status(),
                run.postId(), run.scheduledPostId(), run.error(), view.draft());
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
