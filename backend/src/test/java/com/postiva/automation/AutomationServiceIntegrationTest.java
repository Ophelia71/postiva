package com.postiva.automation;

import com.postiva.automation.AutomationDtos.Brief;
import com.postiva.automation.AutomationDtos.RuleRequest;
import com.postiva.automation.AutomationDtos.RuleResponse;
import com.postiva.automation.AutomationStore.Run;
import com.postiva.post.dto.GeneratePostResponse;
import com.postiva.post.service.PostService;
import com.postiva.scheduler.PublishingScheduler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;

import java.sql.DriverManager;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Real PostgreSQL coverage for the exactly-once guarantees of daily auto-posting. */
@EnabledIfEnvironmentVariable(named = "POSTIVA_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = {
        "spring.config.import=", "postiva.mail.enabled=false",
        "postiva.openai.api-key=automation-test-no-provider-calls",
        "postiva.storage.upload-dir=target/automation-test-uploads",
        "postiva.admin.bootstrap.enabled=false",
        "postiva.scheduler.publish-delay-ms=3600000",
        "postiva.automation.enabled=true",
        "postiva.automation.planner-delay-ms=3600000"
})
class AutomationServiceIntegrationTest {
    private static final String SCHEMA = "automation_" + UUID.randomUUID().toString().replace("-", "");
    private static final String URL = System.getenv("POSTIVA_TEST_DB_URL");
    private static final String DB_USER = System.getenv().getOrDefault("POSTIVA_TEST_DB_USERNAME", "postgres");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("POSTIVA_TEST_DB_PASSWORD", "");

    @Autowired JdbcTemplate jdbc;
    @Autowired AutomationService service;
    @Autowired AutomationStore store;
    @MockBean PublishingScheduler scheduler;
    @MockBean PostService posts;
    @MockBean AutomationNotifier notifier;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        if (URL == null || !URL.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/postiva_migration_test(\\?.*)?")) {
            throw new IllegalStateException("Dedicated local postiva_migration_test database required");
        }
        registry.add("spring.datasource.url", () -> URL);
        registry.add("spring.datasource.username", () -> DB_USER);
        registry.add("spring.datasource.password", () -> DB_PASSWORD);
        registry.add("spring.datasource.hikari.schema", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
    }

    @AfterAll
    static void cleanSchema() throws Exception {
        try (var connection = DriverManager.getConnection(URL, DB_USER, DB_PASSWORD);
             var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }

    // ------------------------------------------------------------ fixtures

    private UUID user(String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, full_name, email, password_hash, role, status) VALUES (?, ?, ?, 'x', 'USER', ?)",
                id, "Daily test", id + "@example.test", status);
        return id;
    }

    private UUID account(UUID userId, String token) {
        return account(userId, token, "FACEBOOK");
    }

    private UUID account(UUID userId, String token, String provider) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO social_accounts (id, user_id, provider, page_id, page_name, access_token_encrypted) "
                + "VALUES (?, ?, ?, ?, 'Cửa hàng thử', ?)", id, userId, provider, "page-" + id, token);
        return id;
    }

    private UUID media(UUID userId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO uploaded_files (id, user_id, file_url, file_name, mime_type, file_size, public_id, storage_provider) "
                + "VALUES (?, ?, '/api/files/x', 'hoa.jpg', 'image/jpeg', 100, ?, 'LOCAL')", id, userId, "public-" + id);
        return id;
    }

    private RuleRequest requestFor(UUID account, List<UUID> mediaIds) {
        return new RuleRequest("Bài mỗi ngày", account, "UTC", Instant.now().plusSeconds(2 * 3600).atZone(ZoneOffset.UTC)
                .toLocalTime().withSecond(0).withNano(0).toString(), List.of(1, 2, 3, 4, 5, 6, 7), "REVIEW", 720,
                new Brief("Bó hoa", "Hoa tươi", null, "Khách", "Giao nhanh", null, null), mediaIds);
    }

    private RuleResponse rule(UUID userId, UUID accountId, Instant publishAt) {
        LocalTime time = publishAt.atZone(ZoneOffset.UTC).toLocalTime().withSecond(0).withNano(0);
        return service.create(userId, new RuleRequest("Bài mỗi ngày", accountId, "UTC", time.toString(),
                List.of(1, 2, 3, 4, 5, 6, 7), "REVIEW", 720,
                new Brief("Bó hoa hướng dương", "Hoa tươi giao trong ngày", null, "Người mua quà",
                        "Tươi lâu, giao nhanh", null, null), List.of()));
    }

    /** The AI is mocked: each call stores a draft the way PostService would and returns it. */
    private void fakeAi() {
        when(posts.generateForUser(any(), any(), any())).thenAnswer(invocation -> {
            UUID userId = invocation.getArgument(0);
            UUID briefId = UUID.randomUUID();
            UUID postId = UUID.randomUUID();
            jdbc.update("INSERT INTO product_briefs (id, user_id, product_name) VALUES (?, ?, 'Bó hoa')", briefId, userId);
            jdbc.update("INSERT INTO generated_posts (id, brief_id, user_id, platform_contents) VALUES (?, ?, ?, "
                    + "'{\"FACEBOOK\":{\"content\":\"Hoa tươi hôm nay\"},\"INSTAGRAM\":{\"caption\":\"Hoa tươi hôm nay\"},"
                    + "\"THREADS\":{\"content\":\"Hoa tươi hôm nay\"},\"_title\":\"Hoa tươi\"}'::jsonb)", postId, briefId, userId);
            return new GeneratePostResponse(postId, briefId, "Hoa tươi", java.util.Map.of(), List.of(), null, null, null, "DRAFT");
        });
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    // ---------------------------------------------------------------- tests

    @Test
    void plannerCreatesOneDraftPerDayEvenWhenRunTwice() {
        fakeAi();
        UUID user = user("ACTIVE");
        RuleResponse rule = rule(user, account(user, "token"), Instant.now().plusSeconds(2 * 3600));

        service.planOnce(Instant.now());
        service.planOnce(Instant.now());

        assertEquals(1, count("SELECT count(*) FROM automation_runs WHERE rule_id = ?", rule.id()));
        assertEquals("PENDING_REVIEW", jdbc.queryForObject(
                "SELECT status FROM automation_runs WHERE rule_id = ?", String.class, rule.id()));
    }

    @Test
    void competingPlannersCannotCreateTheSameDayTwice() throws Exception {
        UUID user = user("ACTIVE");
        RuleResponse rule = rule(user, account(user, "token"), Instant.now().plusSeconds(2 * 3600));
        var ruleRow = store.findRule(user, rule.id()).orElseThrow();
        var planned = AutomationSchedule.dueWithin(ruleRow.daysMask(), ruleRow.timeOfDay(), ZoneOffset.UTC,
                Instant.now(), 720).getFirst();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var attempts = java.util.stream.IntStream.range(0, 2).mapToObj(i -> pool.submit(() -> {
                ready.countDown();
                go.await(5, TimeUnit.SECONDS);
                return store.tryCreateRun(ruleRow.id(), planned.runDate(), planned.at(), UUID.randomUUID(),
                        UUID.randomUUID(), Instant.now().plusSeconds(900));
            })).toList();
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            go.countDown();
            int winners = 0;
            for (var attempt : attempts) {
                Optional<Run> result = attempt.get(10, TimeUnit.SECONDS);
                if (result.isPresent()) winners++;
            }
            assertEquals(1, winners);
        }
        assertEquals(1, count("SELECT count(*) FROM automation_runs WHERE rule_id = ?", rule.id()));
    }

    @Test
    void approvingTwiceSchedulesExactlyOnePost() {
        fakeAi();
        UUID user = user("ACTIVE");
        RuleResponse rule = rule(user, account(user, "token"), Instant.now().plusSeconds(2 * 3600));
        service.planOnce(Instant.now());
        UUID runId = service.runs(user, "PENDING_REVIEW").getFirst().id();

        var approved = service.approve(user, runId);
        assertEquals("SCHEDULED", approved.status());
        assertThrows(ResponseStatusException.class, () -> service.approve(user, runId));

        assertEquals(1, count("SELECT count(*) FROM scheduled_posts WHERE post_id = ?", approved.postId()));
        assertEquals(1, count("SELECT count(*) FROM automation_runs WHERE rule_id = ? AND scheduled_post_id IS NOT NULL",
                rule.id()));
    }

    @Test
    void anotherUserCannotSeeOrActOnARuleOrDraft() {
        fakeAi();
        UUID owner = user("ACTIVE");
        UUID other = user("ACTIVE");
        RuleResponse rule = rule(owner, account(owner, "token"), Instant.now().plusSeconds(2 * 3600));
        service.planOnce(Instant.now());
        UUID runId = service.runs(owner, null).getFirst().id();

        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.get(other, rule.id())).getStatusCode().value());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.approve(other, runId)).getStatusCode().value());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service.skip(other, runId)).getStatusCode().value());
        assertTrue(service.runs(other, null).isEmpty());
        assertTrue(service.list(other).isEmpty());
    }

    @Test
    void ruleCannotUseAnotherUsersChannel() {
        UUID owner = user("ACTIVE");
        UUID other = user("ACTIVE");
        UUID foreignAccount = account(other, "token");
        var thrown = assertThrows(ResponseStatusException.class,
                () -> rule(owner, foreignAccount, Instant.now().plusSeconds(2 * 3600)));
        assertEquals(404, thrown.getStatusCode().value());
    }

    @Test
    void lowQuotaPausesTheRuleInsteadOfSpendingTheManualReserve() {
        fakeAi();
        UUID user = user("ACTIVE");
        RuleResponse rule = rule(user, account(user, "token"), Instant.now().plusSeconds(2 * 3600));
        // Create the user's period, then leave only 2 units, which is below the reserve of 3.
        jdbc.update("INSERT INTO subscription_periods (id, user_id, plan_code, starts_at, ends_at, ai_limit, ai_used) "
                + "VALUES (?, ?, 'FREE', now() - interval '1 day', now() + interval '29 days', 30, 28)", UUID.randomUUID(), user);

        service.planOnce(Instant.now());

        assertEquals("PAUSED", jdbc.queryForObject("SELECT status FROM automation_rules WHERE id = ?", String.class, rule.id()));
        assertEquals("AI_QUOTA_EXHAUSTED", jdbc.queryForObject(
                "SELECT paused_reason FROM automation_rules WHERE id = ?", String.class, rule.id()));
        verify(posts, never()).generateForUser(eq(user), any(), any());
    }

    @Test
    void anUnknownPublishResultPausesTheRuleAndBlocksResume() {
        fakeAi();
        UUID user = user("ACTIVE");
        RuleResponse rule = rule(user, account(user, "token"), Instant.now().plusSeconds(2 * 3600));
        service.planOnce(Instant.now());
        var run = service.approve(user, service.runs(user, "PENDING_REVIEW").getFirst().id());
        jdbc.update("UPDATE scheduled_posts SET status = 'UNKNOWN' WHERE id = ?", run.scheduledPostId());

        service.planOnce(Instant.now());
        assertEquals("UNKNOWN_RESULT", jdbc.queryForObject(
                "SELECT paused_reason FROM automation_rules WHERE id = ?", String.class, rule.id()));
        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.resume(user, rule.id())).getStatusCode().value());

        jdbc.update("UPDATE scheduled_posts SET status = 'PUBLISHED' WHERE id = ?", run.scheduledPostId());
        assertEquals("ACTIVE", service.resume(user, rule.id()).status());
    }

    @Test
    void aFailedTokenPublishPausesButOnlyNewerFailuresCountAfterResume() {
        fakeAi();
        UUID user = user("ACTIVE");
        RuleResponse rule = rule(user, account(user, "token"), Instant.now().plusSeconds(2 * 3600));
        service.planOnce(Instant.now());
        var run = service.approve(user, service.runs(user, "PENDING_REVIEW").getFirst().id());
        jdbc.update("UPDATE scheduled_posts SET status = 'FAILED', failure_code = 'TOKEN', updated_at = now() WHERE id = ?",
                run.scheduledPostId());

        service.planOnce(Instant.now());
        assertEquals("TOKEN", jdbc.queryForObject("SELECT paused_reason FROM automation_rules WHERE id = ?", String.class, rule.id()));

        // The owner fixes the token and resumes: the old failure must not pause the rule again.
        assertEquals("ACTIVE", service.resume(user, rule.id()).status());
        service.planOnce(Instant.now());
        assertEquals("ACTIVE", jdbc.queryForObject("SELECT status FROM automation_rules WHERE id = ?", String.class, rule.id()));
    }

    @Test
    void disconnectedChannelAndLockedOwnerAreNeverPlanned() {
        fakeAi();
        UUID active = user("ACTIVE");
        UUID account = account(active, "token");
        RuleResponse disconnected = rule(active, account, Instant.now().plusSeconds(2 * 3600));
        jdbc.update("UPDATE social_accounts SET disconnected_at = now() WHERE id = ?", account);

        UUID locked = user("ACTIVE");
        RuleResponse lockedRule = rule(locked, account(locked, "token"), Instant.now().plusSeconds(2 * 3600));
        jdbc.update("UPDATE users SET status = 'LOCKED', locked_at = now(), locked_by = ?, lock_reason = 'test' WHERE id = ?",
                active, locked);

        service.planOnce(Instant.now());

        assertEquals("CHANNEL_DISCONNECTED", jdbc.queryForObject(
                "SELECT paused_reason FROM automation_rules WHERE id = ?", String.class, disconnected.id()));
        assertEquals(0, count("SELECT count(*) FROM automation_runs WHERE rule_id IN (?, ?)", disconnected.id(), lockedRule.id()));
    }

    @Test
    void autoModeIsRejectedWhileItsOwnSwitchIsOff() {
        UUID user = user("ACTIVE");
        UUID account = account(user, "token");
        var thrown = assertThrows(ResponseStatusException.class, () -> service.create(user, new RuleRequest(
                "Tự động", account, "UTC", "19:00", List.of(1, 2, 3), "AUTO", 720,
                new Brief("Bó hoa", "Hoa tươi", null, "Khách", "Giao nhanh", null, null), null)));
        assertEquals(400, thrown.getStatusCode().value());
    }

    @Test
    void usersAreLimitedToTheConfiguredNumberOfRules() {
        UUID user = user("ACTIVE");
        UUID account = account(user, "token");
        for (int i = 0; i < 3; i++) rule(user, account, Instant.now().plusSeconds(2 * 3600));
        var thrown = assertThrows(ResponseStatusException.class, () -> rule(user, account, Instant.now().plusSeconds(2 * 3600)));
        assertEquals(409, thrown.getStatusCode().value());
    }

    @Test
    void resumeIsRefusedWhileTheChannelIsStillDisconnected() {
        fakeAi();
        UUID user = user("ACTIVE");
        UUID account = account(user, "token");
        RuleResponse rule = rule(user, account, Instant.now().plusSeconds(2 * 3600));
        jdbc.update("UPDATE social_accounts SET disconnected_at = now() WHERE id = ?", account);
        service.planOnce(Instant.now());
        assertEquals("CHANNEL_DISCONNECTED", jdbc.queryForObject(
                "SELECT paused_reason FROM automation_rules WHERE id = ?", String.class, rule.id()));

        assertEquals(409, assertThrows(ResponseStatusException.class, () -> service.resume(user, rule.id())).getStatusCode().value());
    }

    @Test
    void anInterruptedGenerationIsRetriedWithTheSameAiKeyAndNeverTwice() {
        fakeAi();
        UUID user = user("ACTIVE");
        RuleResponse created = rule(user, account(user, "token"), Instant.now().plusSeconds(2 * 3600));
        var rule = store.findRule(user, created.id()).orElseThrow();
        var planned = AutomationSchedule.dueWithin(rule.daysMask(), rule.timeOfDay(), ZoneOffset.UTC,
                Instant.now(), 720).getFirst();
        UUID key = AutomationService.idempotencyKey(rule, planned.runDate());
        // A planner crashed after claiming the day: its lease is already in the past.
        store.tryCreateRun(rule.id(), planned.runDate(), planned.at(), key, UUID.randomUUID(), Instant.now().minusSeconds(30))
                .orElseThrow();

        service.planOnce(Instant.now());
        service.planOnce(Instant.now());

        assertEquals("PENDING_REVIEW", jdbc.queryForObject(
                "SELECT status FROM automation_runs WHERE rule_id = ?", String.class, rule.id()));
        assertEquals(1, count("SELECT count(*) FROM automation_runs WHERE rule_id = ?", rule.id()));
        verify(posts, org.mockito.Mockito.times(1)).generateForUser(eq(user), any(), eq(key));
    }

    @Test
    void instagramRulesNeedDefaultMediaAndPassItToTheAi() {
        fakeAi();
        UUID user = user("ACTIVE");
        UUID instagram = account(user, "token", "INSTAGRAM");
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.create(user, requestFor(instagram, List.of()))).getStatusCode().value());

        UUID image = media(user);
        RuleResponse rule = service.create(user, requestFor(instagram, List.of(image)));
        assertEquals("INSTAGRAM", rule.platform());
        assertEquals(List.of(image), rule.mediaIds());

        service.planOnce(Instant.now());
        var captor = org.mockito.ArgumentCaptor.forClass(com.postiva.post.dto.GeneratePostRequest.class);
        verify(posts).generateForUser(eq(user), captor.capture(), any());
        assertEquals(List.of(image), captor.getValue().imageIds());
        assertEquals(List.of(com.postiva.common.Platform.INSTAGRAM), captor.getValue().platforms());

        // Instagram keeps its text under "caption"; the review queue must still show it.
        var draft = service.runs(user, "PENDING_REVIEW").getFirst();
        assertEquals("INSTAGRAM", draft.platform());
        assertEquals("Hoa tươi hôm nay", draft.draft().content());
    }

    @Test
    void threadsRulesNeedNoMedia() {
        UUID user = user("ACTIVE");
        RuleResponse rule = service.create(user, requestFor(account(user, "token", "THREADS"), null));
        assertEquals("THREADS", rule.platform());
        assertTrue(rule.mediaIds().isEmpty());
    }

    @Test
    void aRuleCannotUseAnotherUsersMedia() {
        UUID owner = user("ACTIVE");
        UUID other = user("ACTIVE");
        UUID foreign = media(other);
        assertEquals(400, assertThrows(ResponseStatusException.class,
                () -> service.create(owner, requestFor(account(owner, "token"), List.of(foreign)))).getStatusCode().value());
    }

    @Test
    void aDeletedDefaultImagePausesTheRule() {
        fakeAi();
        UUID user = user("ACTIVE");
        UUID image = media(user);
        RuleResponse rule = service.create(user, requestFor(account(user, "token"), List.of(image)));
        jdbc.update("DELETE FROM uploaded_files WHERE id = ?", image);

        service.planOnce(Instant.now());

        assertEquals("MEDIA_MISSING", jdbc.queryForObject(
                "SELECT paused_reason FROM automation_rules WHERE id = ?", String.class, rule.id()));
        verify(posts, never()).generateForUser(eq(user), any(), any());
    }

    @Test
    void theOwnerIsEmailedOnceWhenADraftIsReadyForReview() {
        fakeAi();
        UUID user = user("ACTIVE");
        rule(user, account(user, "token"), Instant.now().plusSeconds(2 * 3600));

        service.planOnce(Instant.now());
        service.planOnce(Instant.now());

        verify(notifier, org.mockito.Mockito.times(1)).draftReady(eq(user), eq("Bài mỗi ngày"), any());
    }

    @Test
    void ruleCanBeEditedAndKeepsItsOwnership() {
        UUID user = user("ACTIVE");
        UUID account = account(user, "token");
        RuleResponse rule = rule(user, account, Instant.now().plusSeconds(2 * 3600));
        var edited = new RuleRequest("Đổi tên", account, "UTC", "08:30", List.of(1, 3, 5), "REVIEW", 360,
                new Brief("Sản phẩm mới", "Mô tả mới", null, "Khách mới", "Nổi bật mới", null, null), List.of());

        RuleResponse updated = service.update(user, rule.id(), edited);

        assertEquals("Đổi tên", updated.name());
        assertEquals("08:30", updated.timeOfDay());
        assertEquals(List.of(1, 3, 5), updated.daysOfWeek());
        assertEquals(360, updated.leadMinutes());
        assertEquals("Sản phẩm mới", updated.brief().productName());
        UUID other = user("ACTIVE");
        assertEquals(404, assertThrows(ResponseStatusException.class,
                () -> service.update(other, rule.id(), edited)).getStatusCode().value());
    }

    @Test
    void pauserSkipsAutomaticallyButKeepsDraftsOnUserPause() {        fakeAi();
        UUID user = user("ACTIVE");
        RuleResponse rule = rule(user, account(user, "token"), Instant.now().plusSeconds(2 * 3600));
        service.planOnce(Instant.now());
        assertEquals(1, service.runs(user, "PENDING_REVIEW").size());

        service.pause(user, rule.id());
        assertEquals(1, service.runs(user, "PENDING_REVIEW").size());

        UUID second = user("ACTIVE");
        RuleResponse other = rule(second, account(second, "token"), Instant.now().plusSeconds(2 * 3600));
        service.planOnce(Instant.now());
        assertEquals(1, service.runs(second, "PENDING_REVIEW").size());
        new AutomationPauser(jdbc).pauseAllForUser(second, AutomationPauser.REASON_USER_LOCKED);
        assertEquals(0, service.runs(second, "PENDING_REVIEW").size());
        assertEquals("USER_LOCKED", jdbc.queryForObject(
                "SELECT paused_reason FROM automation_rules WHERE id = ?", String.class, other.id()));
    }
}
