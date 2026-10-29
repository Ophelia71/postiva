package com.postiva.social.sync;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.postiva.ai.service.OpenAiService;
import com.postiva.auth.security.JwtService;
import com.postiva.common.Platform;
import com.postiva.common.PostStatus;
import com.postiva.mail.EmailService;
import com.postiva.post.entity.GeneratedPost;
import com.postiva.post.entity.ProductBrief;
import com.postiva.post.repository.GeneratedPostRepository;
import com.postiva.post.repository.ProductBriefRepository;
import com.postiva.scheduler.PublishingScheduler;
import com.postiva.scheduler.ScheduledPostStatus;
import com.postiva.scheduler.entity.ScheduledPost;
import com.postiva.scheduler.repository.ScheduledPostRepository;
import com.postiva.social.client.FacebookGraphClient;
import com.postiva.social.client.InstagramGraphClient;
import com.postiva.social.client.ThreadsGraphClient;
import com.postiva.social.entity.SocialAccount;
import com.postiva.social.insights.PostMetrics;
import com.postiva.social.repository.SocialAccountRepository;
import com.postiva.social.security.TokenCipher;
import com.postiva.user.entity.User;
import com.postiva.user.repository.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/** Opt-in, real PostgreSQL tests of channel sync (import, engagement, removals) and channel deletion. */
@EnabledIfEnvironmentVariable(named = "POSTIVA_TEST_DB_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=", "server.address=127.0.0.1", "postiva.mail.enabled=false",
        "postiva.openai.api-key=isolated-test-no-provider-calls", "postiva.storage.upload-dir=target/channel-sync-uploads",
        "app.jwt.secret=only-for-channel-sync-isolated-tests-signing-key",
        "postiva.security.token-encryption-key=only-for-channel-sync-isolated-tests-token-key",
        "postiva.admin.bootstrap.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ChannelSyncIntegrationTest {
    private static final String SCHEMA = "channel_sync_" + UUID.randomUUID().toString().replace("-", "");
    private static final String URL = System.getenv("POSTIVA_TEST_DB_URL");
    private static final String DB_USER = System.getenv().getOrDefault("POSTIVA_TEST_DB_USERNAME", "postgres");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("POSTIVA_TEST_DB_PASSWORD", "");

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired SocialAccountRepository accounts;
    @Autowired GeneratedPostRepository posts;
    @Autowired ProductBriefRepository briefs;
    @Autowired ScheduledPostRepository schedules;
    @Autowired PasswordEncoder passwords;
    @Autowired TokenCipher tokenCipher;
    @Autowired JwtService jwt;
    @MockBean EmailService email;
    @MockBean OpenAiService ai;
    @MockBean PublishingScheduler scheduler;
    @MockBean FacebookGraphClient facebook;
    @MockBean InstagramGraphClient instagram;
    @MockBean ThreadsGraphClient threads;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        if (URL == null || !URL.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/postiva_migration_test(\\?.*)?"))
            throw new IllegalStateException("Dedicated local test database required");
        registry.add("spring.datasource.url", () -> URL);
        registry.add("spring.datasource.username", () -> DB_USER);
        registry.add("spring.datasource.password", () -> DB_PASSWORD);
        registry.add("spring.datasource.hikari.schema", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
    }

    @AfterAll static void cleanup() throws Exception {
        try (var connection = DriverManager.getConnection(URL, DB_USER, DB_PASSWORD); var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }

    @BeforeEach void resetClients() {
        reset(facebook, instagram, threads);
    }

    @Test void instagramSyncImportsRefreshesAndOnlyRemovesInsideTheWindowItRead() {
        User user = user();
        String token = jwt.generateToken(user);
        SocialAccount ig = account(user, Platform.INSTAGRAM, "ig-" + UUID.randomUUID());
        Instant now = Instant.now();
        PostMetrics p1 = igPost("p1", now.minus(2, ChronoUnit.DAYS), 10);
        PostMetrics p2 = igPost("p2", now.minus(3, ChronoUnit.DAYS), 5);
        PostMetrics p3 = igPost("p3", now.minus(4, ChronoUnit.DAYS), 1);

        when(instagram.getRecentMediaMetrics(eq(ig.getPageId()), anyString(), any(), anyInt())).thenReturn(List.of(p1, p2, p3));
        JsonNode first = sync(token, ig);
        assertEquals(3, first.path("imported").asInt());
        assertTrue(first.path("metricsAvailable").asBoolean());
        JsonNode imported = postOf(token, "p1-caption");
        assertEquals("Instagram", imported.path("platform").asText());
        assertEquals(10, imported.path("metrics").path("likes").asInt());
        assertEquals("https://instagram.test/p1", imported.path("permalinkUrl").asText());

        // p2 was deleted on Instagram; p1 got more likes.
        when(instagram.getRecentMediaMetrics(eq(ig.getPageId()), anyString(), any(), anyInt()))
                .thenReturn(List.of(p1.withInsights(12, 3, null, null), p3));
        JsonNode second = sync(token, ig);
        assertEquals(2, second.path("updated").asInt());
        assertEquals(1, second.path("removed").asInt());
        assertEquals(12, postOf(token, "p1-caption").path("metrics").path("likes").asInt());

        // Only p1 listed: p3 is older than anything read, so it is not judged deleted.
        when(instagram.getRecentMediaMetrics(eq(ig.getPageId()), anyString(), any(), anyInt())).thenReturn(List.of(p1));
        assertEquals(0, sync(token, ig).path("removed").asInt());
        // An empty reply never deletes anything.
        when(instagram.getRecentMediaMetrics(eq(ig.getPageId()), anyString(), any(), anyInt())).thenReturn(List.of());
        assertEquals(0, sync(token, ig).path("removed").asInt());
        assertEquals(2, scheduleCount(ig));
    }

    @Test void connectingThreadsSyncsPostsAndEngagementInTheBackground() throws InterruptedException {
        User user = user();
        String token = jwt.generateToken(user);
        String threadsId = "thr-" + UUID.randomUUID();
        when(threads.getMe("threads-token")).thenReturn(new ThreadsGraphClient.ThreadsProfile(threadsId, "shop"));
        when(threads.getRecentThreads(eq("threads-token"), anyInt())).thenReturn(List.of(new PostMetrics(
                "t1", Platform.THREADS, Instant.now().minus(1, ChronoUnit.DAYS), "Bài Threads", "TEXT_POST",
                null, null, null, null, "https://threads.test/t1")));
        when(threads.getThreadInsights("t1", "threads-token"))
                .thenReturn(Map.of("views", 300, "likes", 7, "replies", 2, "reposts", 1, "quotes", 1));

        Result connected = call("POST", "/api/social/threads/manual", token,
                Map.of("accountId", threadsId, "accountName", "shop", "accessToken", "threads-token"));
        assertEquals(200, connected.status(), connected.body().toString());

        Map<String, Object> metrics = null;
        for (int attempt = 0; attempt < 100 && metrics == null; attempt++) {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT metric.* FROM post_metrics metric JOIN scheduled_posts scheduled ON scheduled.id = metric.scheduled_post_id
                    JOIN social_accounts account ON account.id = scheduled.social_account_id WHERE account.page_id = ?
                    """, threadsId);
            if (rows.isEmpty()) Thread.sleep(100); else metrics = rows.getFirst();
        }
        assertNotNull(metrics, "background sync did not store engagement");
        assertEquals(300, metrics.get("views"));
        assertEquals(7, metrics.get("likes"));
        assertEquals(2, metrics.get("comments"));
        assertEquals(2, metrics.get("shares"));
    }

    @Test void deletingAChannelRemovesItsHistoryButKeepsSharedPostsAndAiUsage() {
        User user = user();
        String token = jwt.generateToken(user);
        SocialAccount page = account(user, Platform.FACEBOOK, "page-" + UUID.randomUUID());
        SocialAccount ig = account(user, Platform.INSTAGRAM, "ig-" + UUID.randomUUID());
        Instant now = Instant.now();
        when(facebook.getPagePostMetrics(eq(page.getPageId()), anyString(), anyInt())).thenReturn(List.of(
                new PostMetrics("f1", Platform.FACEBOOK, now.minus(1, ChronoUnit.DAYS), "FB 1", "TEXT", 4, 1, 0, null, "https://fb.test/f1"),
                new PostMetrics("f2", Platform.FACEBOOK, now.minus(2, ChronoUnit.DAYS), "FB 2", "PHOTO", 9, 2, 1, null, "https://fb.test/f2")));
        assertEquals(2, sync(token, page).path("imported").asInt());

        GeneratedPost shared = post(user, "Bài dùng chung");
        schedule(shared, page, ScheduledPostStatus.PUBLISHED);
        schedule(shared, ig, ScheduledPostStatus.SCHEDULED);
        GeneratedPost onlyHere = post(user, "Bài chỉ của Page");
        ScheduledPost failed = schedule(onlyHere, page, ScheduledPostStatus.FAILED);
        UUID usageId = aiUsageFor(user, onlyHere);

        JsonNode impact = call("GET", "/api/social/meta/accounts/" + page.getId() + "/deletion-impact", token, null).body().path("data");
        assertEquals(3, impact.path("publishedPosts").asInt());
        assertEquals(1, impact.path("failedPosts").asInt());

        failed.setStatus(ScheduledPostStatus.PUBLISHING);
        schedules.saveAndFlush(failed);
        assertEquals(409, call("DELETE", "/api/social/meta/accounts/" + page.getId(), token, null).status());
        failed.setStatus(ScheduledPostStatus.FAILED);
        schedules.saveAndFlush(failed);

        Result deleted = call("DELETE", "/api/social/meta/accounts/" + page.getId(), token, null);
        assertEquals(200, deleted.status(), deleted.body().toString());
        assertEquals(0, scheduleCount(page));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM generated_posts WHERE id = ?", Integer.class, onlyHere.getId()));
        // The shared post keeps its Instagram schedule; AI usage keeps its row and loses only the post link.
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM scheduled_posts WHERE post_id = ? AND social_account_id = ?",
                Integer.class, shared.getId(), ig.getId()));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM ai_usage_requests WHERE id = ? AND post_id IS NULL", Integer.class, usageId));
        assertTrue(jdbc.queryForObject("SELECT disconnected_at IS NOT NULL AND access_token_encrypted IS NULL FROM social_accounts WHERE id = ?",
                Boolean.class, page.getId()));

        // Reconnecting brings the Page's posts back.
        jdbc.update("UPDATE social_accounts SET disconnected_at = NULL, access_token_encrypted = ? WHERE id = ?",
                tokenCipher.encrypt("page-token"), page.getId());
        assertEquals(2, sync(token, page).path("imported").asInt());
    }

    @Test void postsOfAChannelRemovedBeforeDeletionExistedAreLeftOutOfReports() {
        User user = user();
        String token = jwt.generateToken(user);
        SocialAccount removed = account(user, Platform.FACEBOOK, "page-" + UUID.randomUUID());
        SocialAccount connected = account(user, Platform.FACEBOOK, "page-" + UUID.randomUUID());
        GeneratedPost old = post(user, "Bài của kênh đã gỡ");
        schedule(old, removed, ScheduledPostStatus.PUBLISHED);
        GeneratedPost plannedOld = post(user, "Bản hẹn giờ của kênh đã gỡ");
        plannedOld.setStatus(PostStatus.PLANNED);
        posts.saveAndFlush(plannedOld);
        schedule(plannedOld, removed, ScheduledPostStatus.FAILED);
        GeneratedPost current = post(user, "Bài của kênh đang nối");
        schedule(current, connected, ScheduledPostStatus.PUBLISHED);
        // The old disconnect only stamped the account and kept its schedules.
        jdbc.update("UPDATE social_accounts SET disconnected_at = now(), access_token_encrypted = NULL WHERE id = ?", removed.getId());

        List<String> contents = new java.util.ArrayList<>();
        call("GET", "/api/app-data/posts", token, null).body().path("data").forEach(post -> contents.add(post.path("content").asText()));
        assertEquals(List.of("Bài của kênh đang nối"), contents);
        assertEquals(1, call("GET", "/api/app-data/overview", token, null).body().path("data").path("totalPosts").asInt());
    }

    private static PostMetrics igPost(String id, Instant at, int likes) {
        return new PostMetrics(id, Platform.INSTAGRAM, at, id + "-caption", "IMAGE", likes, 1, null, null, "https://instagram.test/" + id);
    }

    private JsonNode sync(String token, SocialAccount account) {
        Result result = call("POST", "/api/social/meta/accounts/" + account.getId() + "/sync", token, null);
        assertEquals(200, result.status(), result.body().toString());
        return result.body().path("data");
    }

    private JsonNode postOf(String token, String content) {
        for (JsonNode post : call("GET", "/api/app-data/posts", token, null).body().path("data")) {
            if (content.equals(post.path("content").asText())) return post;
        }
        throw new AssertionError("post not found: " + content);
    }

    private int scheduleCount(SocialAccount account) {
        return jdbc.queryForObject("SELECT count(*) FROM scheduled_posts WHERE social_account_id = ?", Integer.class, account.getId());
    }

    private User user() {
        User user = new User();
        user.setFullName("Channel sync fixture");
        user.setEmail(UUID.randomUUID() + "@example.test");
        user.setPasswordHash(passwords.encode("isolated-test-password-123"));
        return users.saveAndFlush(user);
    }

    private SocialAccount account(User user, Platform provider, String pageId) {
        SocialAccount account = new SocialAccount();
        account.setUserId(user.getId());
        account.setProvider(provider);
        account.setProviderAccountId(pageId);
        account.setPageId(pageId);
        account.setPageName("Fixture " + provider);
        account.setAccessTokenEncrypted(tokenCipher.encrypt("page-token"));
        if (provider == Platform.INSTAGRAM) account.setAccountMetadata(new java.util.HashMap<>(Map.of("apiMode", "INSTAGRAM_LOGIN")));
        return accounts.saveAndFlush(account);
    }

    private GeneratedPost post(User user, String content) {
        ProductBrief brief = new ProductBrief();
        brief.setUserId(user.getId());
        brief.setProductName("Fixture");
        brief.setTargetAudience("Fixture");
        brief.setGoal("Fixture");
        brief.setTone("Fixture");
        brief.setPlatforms(List.of(Platform.FACEBOOK));
        brief = briefs.saveAndFlush(brief);
        GeneratedPost post = new GeneratedPost();
        post.setBriefId(brief.getId());
        post.setUserId(user.getId());
        post.setPlatformContents(new java.util.HashMap<>(Map.of("FACEBOOK", Map.of("content", content))));
        post.setStatus(PostStatus.PUBLISHED);
        return posts.saveAndFlush(post);
    }

    private ScheduledPost schedule(GeneratedPost post, SocialAccount account, ScheduledPostStatus status) {
        ScheduledPost scheduled = new ScheduledPost();
        scheduled.setPostId(post.getId());
        scheduled.setSocialAccountId(account.getId());
        scheduled.setPlatform(account.getProvider());
        scheduled.setScheduledTime(Instant.now().minus(5, ChronoUnit.DAYS));
        scheduled.setStatus(status);
        if (status == ScheduledPostStatus.PUBLISHED) scheduled.setPublishedAt(Instant.now().minus(5, ChronoUnit.DAYS));
        return schedules.saveAndFlush(scheduled);
    }

    private UUID aiUsageFor(User user, GeneratedPost post) {
        UUID periodId = UUID.randomUUID();
        jdbc.update("INSERT INTO subscription_periods (id, user_id, plan_code, starts_at, ends_at, ai_limit) VALUES (?, ?, 'FREE', ?, ?, 30)",
                periodId, user.getId(), Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS)), Timestamp.from(Instant.now().plus(29, ChronoUnit.DAYS)));
        UUID usageId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO ai_usage_requests (id, user_id, period_id, idempotency_key, request_hash, status, model, post_id)
                VALUES (?, ?, ?, ?, ?, 'SUCCEEDED', 'gpt-4o-mini', ?)
                """, usageId, user.getId(), periodId, UUID.randomUUID(), "0".repeat(64), post.getId());
        return usageId;
    }

    private Result call(String method, String path, String token, Object body) {
        try {
            var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json");
            if (token != null) builder.header("Authorization", "Bearer " + token);
            builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new Result(response.statusCode(), json.readTree(response.body().isBlank() ? "{}" : response.body()));
        } catch (Exception e) {
            throw new AssertionError("HTTP fixture failed", e);
        }
    }

    private record Result(int status, JsonNode body) { }
}
