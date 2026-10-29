package com.postiva.calendar;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.postiva.ai.dto.AiPostResult;
import com.postiva.ai.dto.ProviderTokenUsage;
import com.postiva.ai.dto.WeeklyIdeasResult;
import com.postiva.ai.dto.WeeklyPlanPromptInput;
import com.postiva.ai.service.OpenAiService;
import com.postiva.auth.security.JwtService;
import com.postiva.common.Platform;
import com.postiva.mail.EmailService;
import com.postiva.scheduler.PublishingScheduler;
import com.postiva.social.client.FacebookGraphClient;
import com.postiva.social.entity.SocialAccount;
import com.postiva.social.insights.PostMetrics;
import com.postiva.social.repository.SocialAccountRepository;
import com.postiva.social.security.TokenCipher;
import com.postiva.usage.UsageProperties;
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
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/** Opt-in, real PostgreSQL tests of the weekly plan flow with Meta and OpenAI replaced by fixtures. */
@EnabledIfEnvironmentVariable(named = "POSTIVA_TEST_DB_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=", "server.address=127.0.0.1", "postiva.mail.enabled=false",
        "postiva.openai.api-key=isolated-test-no-provider-calls", "postiva.storage.upload-dir=target/weekly-plan-uploads",
        "app.jwt.secret=only-for-weekly-plan-isolated-tests-signing-key",
        "postiva.security.token-encryption-key=only-for-weekly-plan-isolated-tests-token-key",
        "postiva.admin.bootstrap.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class WeeklyPlanIntegrationTest {
    private static final String SCHEMA = "weekly_plan_" + UUID.randomUUID().toString().replace("-", "");
    private static final String URL = System.getenv("POSTIVA_TEST_DB_URL");
    private static final String DB_USER = System.getenv().getOrDefault("POSTIVA_TEST_DB_USERNAME", "postgres");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("POSTIVA_TEST_DB_PASSWORD", "");
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired SocialAccountRepository accounts;
    @Autowired PasswordEncoder passwords;
    @Autowired TokenCipher tokenCipher;
    @Autowired JwtService jwt;
    @Autowired UsageProperties limits;
    @MockBean EmailService email;
    @MockBean OpenAiService ai;
    @MockBean FacebookGraphClient facebook;
    @MockBean PublishingScheduler scheduler;
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

    @BeforeEach void fixtures() {
        reset(ai, facebook);
        jdbc.update("DELETE FROM ai_daily_budgets");
        limits.setFreeAiLimit(30);
        limits.setGlobalDailyAttempts(100);
        limits.setUserDailyAttempts(20);
        when(facebook.getPagePostMetrics(anyString(), anyString(), anyInt())).thenReturn(pastPosts());
        when(ai.generateWeeklyIdeas(any(), any())).thenAnswer(invocation -> {
            WeeklyPlanPromptInput input = invocation.getArgument(0);
            Consumer<ProviderTokenUsage> observer = invocation.getArgument(1);
            observer.accept(new ProviderTokenUsage(800, 0, 300, "gpt-4o-mini", "fixture-plan"));
            List<WeeklyIdeasResult.Idea> ideas = new ArrayList<>();
            for (String slot : input.slots()) {
                ideas.add(new WeeklyIdeasResult.Idea(slot.substring(0, 10), "", "Video", "Ý tưởng " + slot.substring(0, 10),
                        "Mở đầu bằng câu hỏi", "Video được tương tác tốt nhất."));
            }
            return new WeeklyIdeasResult("Tuần tập trung vào video.", ideas);
        });
        when(ai.generatePost(any(), any(), any(), any())).thenAnswer(invocation -> {
            Consumer<ProviderTokenUsage> observer = invocation.getArgument(3);
            observer.accept(new ProviderTokenUsage(500, 0, 100, "gpt-4o-mini", "fixture-post"));
            return new AiPostResult("Tiêu đề", Map.of("FACEBOOK", Map.of("title", "Tiêu đề", "content", "Nội dung bài tuần")),
                    List.of("#caphe"), "Nhắn shop ngay", "Ảnh ly cà phê");
        });
    }

    @Test void planIsBuiltFromPastPostsReviewedThenScheduled() throws Exception {
        User user = user();
        String token = jwt.generateToken(user);
        SocialAccount page = facebookPage(user);

        Result created = call("POST", "/api/weekly-plans", token, request(page.getId(), nextMonday(), 3));
        assertEquals(200, created.status(), created.body().toString());
        assertEquals("GENERATING", created.body().path("data").path("status").asText());
        String planId = created.body().path("data").path("id").asText();

        JsonNode plan = awaitStatus(token, planId, "READY");
        JsonNode items = plan.path("items");
        assertEquals(3, items.size());
        assertEquals("Tuần tập trung vào video.", plan.path("strategy").asText());
        // Past Friday-20:00 videos did best, so the plan posts at 20:00 and includes Friday.
        assertEquals(20, plan.path("performance").path("bestHour").asInt());
        List<DayOfWeek> days = new ArrayList<>();
        for (JsonNode item : items) {
            assertEquals("DRAFT", item.path("status").asText());
            assertEquals("Nội dung bài tuần", item.path("post").path("platformContents").path("FACEBOOK").path("content").asText());
            ZonedDateTime at = Instant.parse(item.path("scheduledTime").asText()).atZone(VN);
            assertEquals(20, at.getHour());
            days.add(at.getDayOfWeek());
        }
        assertTrue(days.contains(DayOfWeek.FRIDAY), days.toString());
        // One unit for the ideas, one per draft.
        assertEquals(4, jdbc.queryForObject("SELECT ai_used FROM subscription_periods WHERE user_id = ?", Integer.class, user.getId()));

        String skippedId = items.get(0).path("id").asText();
        Result skipped = call("PATCH", "/api/weekly-plans/" + planId + "/items/" + skippedId, token, Map.of("skipped", true));
        assertEquals("SKIPPED", skipped.body().path("data").path("items").get(0).path("status").asText());

        Result approved = call("POST", "/api/weekly-plans/" + planId + "/approve", token, null);
        assertEquals(200, approved.status(), approved.body().toString());
        assertEquals("APPROVED", approved.body().path("data").path("status").asText());
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM scheduled_posts WHERE social_account_id = ? AND status = 'SCHEDULED'",
                Integer.class, page.getId()));
        assertEquals(409, call("DELETE", "/api/weekly-plans/" + planId, token, null).status());
        assertEquals(409, call("POST", "/api/weekly-plans/" + planId + "/approve", token, null).status());
    }

    @Test void planNeedingMoreQuotaThanLeftIsRefusedUpFront() {
        limits.setFreeAiLimit(3);
        User user = user();
        String token = jwt.generateToken(user);
        SocialAccount page = facebookPage(user);

        Result refused = call("POST", "/api/weekly-plans", token, request(page.getId(), nextMonday(), 3));

        assertEquals(409, refused.status());
        assertTrue(refused.body().path("message").asText().contains("cần 4 lượt AI"), refused.body().toString());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM content_calendars WHERE user_id = ?", Integer.class, user.getId()));
    }

    @Test void plansAndAccountsOfOtherUsersAreNotReachable() throws Exception {
        User owner = user();
        String ownerToken = jwt.generateToken(owner);
        SocialAccount page = facebookPage(owner);
        String planId = call("POST", "/api/weekly-plans", ownerToken, request(page.getId(), nextMonday(), 1))
                .body().path("data").path("id").asText();
        awaitStatus(ownerToken, planId, "READY");

        String strangerToken = jwt.generateToken(user());
        assertEquals(404, call("GET", "/api/weekly-plans/" + planId, strangerToken, null).status());
        assertEquals(404, call("POST", "/api/weekly-plans/" + planId + "/approve", strangerToken, null).status());
        assertEquals(404, call("POST", "/api/weekly-plans", strangerToken, request(page.getId(), nextMonday(), 1)).status());
        assertEquals(0, call("GET", "/api/weekly-plans", strangerToken, null).body().path("data").size());
    }

    @Test void pastWeekIsRejected() {
        User user = user();
        Result result = call("POST", "/api/weekly-plans", jwt.generateToken(user),
                request(facebookPage(user).getId(), LocalDate.now(VN).minusDays(1), 3));
        assertEquals(400, result.status());
    }

    private JsonNode awaitStatus(String token, String planId, String status) throws InterruptedException {
        JsonNode plan = null;
        for (int attempt = 0; attempt < 100; attempt++) {
            plan = call("GET", "/api/weekly-plans/" + planId, token, null).body().path("data");
            if (status.equals(plan.path("status").asText())) return plan;
            if ("FAILED".equals(plan.path("status").asText())) throw new AssertionError("Plan failed: " + plan);
            Thread.sleep(100);
        }
        throw new AssertionError("Plan did not reach " + status + ": " + plan);
    }

    private static LocalDate nextMonday() {
        return LocalDate.now(VN).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }

    private Map<String, Object> request(UUID accountId, LocalDate start, int posts) {
        Map<String, Object> body = new HashMap<>();
        body.put("socialAccountId", accountId);
        body.put("startDate", start.toString());
        body.put("postsPerWeek", posts);
        body.put("timezone", VN.getId());
        body.put("productName", "Cà phê muối");
        body.put("productDescription", "Cà phê muối pha phin");
        body.put("targetAudience", "Dân văn phòng");
        body.put("highlights", "Giao trong 30 phút");
        body.put("goal", "Tăng đơn cuối tuần");
        return body;
    }

    /** Four weeks of Friday 20:00 videos that beat Monday 9:00 text posts. */
    private static List<PostMetrics> pastPosts() {
        LocalDate friday = LocalDate.now(VN).with(TemporalAdjusters.previous(DayOfWeek.FRIDAY));
        List<PostMetrics> posts = new ArrayList<>();
        for (int week = 0; week < 4; week++) {
            Instant video = friday.minusWeeks(week).atTime(20, 0).atZone(VN).toInstant();
            Instant text = friday.minusWeeks(week).minusDays(4).atTime(9, 0).atZone(VN).toInstant();
            posts.add(new PostMetrics("v" + week, Platform.FACEBOOK, video, "Video pha chế #caphe", "VIDEO", 40, 6, 3, null));
            posts.add(new PostMetrics("t" + week, Platform.FACEBOOK, text, "Thông báo", "TEXT", 5, 0, 0, null));
        }
        return posts;
    }

    private User user() {
        User user = new User();
        user.setFullName("Weekly plan fixture");
        user.setEmail(UUID.randomUUID() + "@example.test");
        user.setPasswordHash(passwords.encode("isolated-test-password-123"));
        return users.saveAndFlush(user);
    }

    private SocialAccount facebookPage(User user) {
        SocialAccount account = new SocialAccount();
        account.setUserId(user.getId());
        account.setProvider(Platform.FACEBOOK);
        String pageId = "page-" + UUID.randomUUID();
        account.setProviderAccountId(pageId);
        account.setPageId(pageId);
        account.setPageName("Shop Test Page");
        account.setAccessTokenEncrypted(tokenCipher.encrypt("fixture-page-token"));
        return accounts.saveAndFlush(account);
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
