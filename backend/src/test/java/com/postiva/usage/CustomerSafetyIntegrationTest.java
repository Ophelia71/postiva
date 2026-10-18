package com.postiva.usage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.postiva.ai.dto.AiPostResult;
import com.postiva.ai.dto.ProviderTokenUsage;
import com.postiva.ai.service.OpenAiService;
import com.postiva.auth.security.PersistentRateLimiter;
import com.postiva.auth.security.JwtService;
import com.postiva.common.RateLimitException;
import com.postiva.mail.EmailService;
import com.postiva.scheduler.PublishingScheduler;
import com.postiva.user.entity.User;
import com.postiva.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named = "POSTIVA_TEST_DB_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=", "server.address=127.0.0.1", "postiva.mail.enabled=false",
        "postiva.openai.api-key=isolated-test-no-provider-calls", "postiva.storage.upload-dir=target/customer-safety-uploads",
        "app.jwt.secret=only-for-customer-safety-isolated-tests-signing-key",
        "postiva.admin.bootstrap.enabled=false", "postiva.security.login-attempts-per-15-minutes=3"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CustomerSafetyIntegrationTest {
    private static final String SCHEMA = "customer_safety_" + UUID.randomUUID().toString().replace("-", "");
    private static final String URL = System.getenv("POSTIVA_TEST_DB_URL");
    private static final String DB_USER = System.getenv().getOrDefault("POSTIVA_TEST_DB_USERNAME", "postgres");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("POSTIVA_TEST_DB_PASSWORD", "");
    private static final String PASSWORD = "isolated-test-password-123";
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtService jwt;
    @Autowired AiUsageService usage;
    @Autowired AiCostService costs;
    @Autowired com.postiva.config.AiCostProperties costProperties;
    @Autowired VerifiedSubscriptionPlanService subscriptions;
    @Autowired UsageProperties limits;
    @Autowired PersistentRateLimiter rateLimiter;
    @MockBean EmailService email;
    @MockBean OpenAiService ai;
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

    @BeforeEach void resetCounters() {
        reset(ai);
        jdbc.update("DELETE FROM request_rate_limits");
        jdbc.update("DELETE FROM ai_daily_budgets");
        limits.setFreeAiLimit(2);
        limits.setGlobalDailyAttempts(100);
        limits.setUserDailyAttempts(20);
        doAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "SMTP must not hold a DB transaction");
            return null;
        }).when(email).sendRegistrationSuccess(anyString(), anyString());
        when(ai.generatePost(any(), any(), any())).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "Provider I/O must not hold a DB transaction");
            Consumer<ProviderTokenUsage> observer = invocation.getArgument(2);
            observer.accept(new ProviderTokenUsage(500, 0, 100, "gpt-4o-mini", "fixture-request"));
            return result();
        });
    }

    @Test void loginAttemptsPersistAndCannotBeBypassedByForwardedHeader() {
        User user = user();
        for (int i = 0; i < 3; i++) assertEquals(401, call("POST", "/api/auth/login", null, null,
                Map.of("email", user.getEmail().toUpperCase(), "password", "wrong-password")).status());
        Result blocked = call("POST", "/api/auth/login", null, null, Map.of("email", user.getEmail(), "password", PASSWORD));
        assertEquals(429, blocked.status());
        assertTrue(Long.parseLong(blocked.retryAfter()) > 0);
        assertThrows(RateLimitException.class, () -> new PersistentRateLimiter(jdbc).consume("login-account", user.getEmail(), 3, 900));
        assertEquals(3, jdbc.queryForObject("SELECT attempts FROM request_rate_limits WHERE bucket_key = ?", Integer.class,
                "login-account:" + PersistentRateLimiter.sha256(user.getEmail())));
    }

    @Test void registrationResendIsLimitedAndOtpLockSurvivesFailedResponses() {
        String address = UUID.randomUUID() + "@example.test";
        AtomicReference<String> otp = new AtomicReference<>();
        doAnswer(inv -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "OTP email must be sent after commit");
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM pending_registrations WHERE email = ?", Integer.class, address));
            otp.set(inv.getArgument(2)); return null;
        }).when(email).sendRegistrationOtp(eq(address), anyString(), anyString(), anyLong());
        Map<String, Object> registration = Map.of("fullName", "OTP fixture", "email", address, "password", PASSWORD);
        assertEquals(202, call("POST", "/api/auth/register", null, null, registration).status());
        verify(email, timeout(5_000)).sendRegistrationOtp(eq(address), anyString(), anyString(), anyLong());
        assertEquals(429, call("POST", "/api/auth/register", null, null, registration).status());
        String wrong = "000000".equals(otp.get()) ? "111111" : "000000";
        for (int i = 0; i < 5; i++) assertEquals(401,
                call("POST", "/api/auth/register/verify", null, null, Map.of("email", address, "code", wrong)).status());
        assertEquals(5, jdbc.queryForObject("SELECT failed_attempts FROM pending_registrations WHERE email = ?", Integer.class, address));
        assertEquals(401, call("POST", "/api/auth/register/verify", null, null, Map.of("email", address, "code", otp.get())).status());
        assertFalse(users.existsByEmailIgnoreCase(address));
        jdbc.update("UPDATE request_rate_limits SET expires_at = clock_timestamp() - INTERVAL '1 second' WHERE bucket_key = ?",
                "register-cooldown:" + PersistentRateLimiter.sha256(address));
        assertEquals(202, call("POST", "/api/auth/register", null, null, registration).status());
        verify(email, timeout(5_000).times(2)).sendRegistrationOtp(eq(address), anyString(), anyString(), anyLong());
        Result verified = call("POST", "/api/auth/register/verify", null, null, Map.of("email", address, "code", otp.get()));
        assertEquals(200, verified.status(), verified.body().toString());
        assertEquals("USER", verified.body().path("role").asText());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM subscription_periods WHERE user_id = ?", Integer.class,
                UUID.fromString(verified.body().path("userId").asText())));
    }

    @Test void otpCanBeUsedOnceUnderConcurrentVerificationAndExistingEmailHasGenericResponse() {
        String address = UUID.randomUUID() + "@example.test";
        AtomicReference<String> otp = new AtomicReference<>();
        doAnswer(inv -> { otp.set(inv.getArgument(2)); return null; }).when(email).sendRegistrationOtp(eq(address), anyString(), anyString(), anyLong());
        Map<String, Object> registration = Map.of("fullName", "Concurrent fixture", "email", address, "password", PASSWORD);
        Result initialRegistration = call("POST", "/api/auth/register", null, null, registration);
        assertEquals(202, initialRegistration.status());
        verify(email, timeout(5_000)).sendRegistrationOtp(eq(address), anyString(), anyString(), anyLong());
        var one = CompletableFuture.supplyAsync(() -> call("POST", "/api/auth/register/verify", null, null, Map.of("email", address, "code", otp.get())));
        var two = CompletableFuture.supplyAsync(() -> call("POST", "/api/auth/register/verify", null, null, Map.of("email", address, "code", otp.get())));
        assertEquals(List.of(200, 401), List.of(one.join().status(), two.join().status()).stream().sorted().toList());
        jdbc.update("DELETE FROM request_rate_limits");
        Result existingRegistration = call("POST", "/api/auth/register", null, null, registration);
        assertEquals(202, existingRegistration.status());
        assertEquals(initialRegistration.body(), existingRegistration.body());
        verify(email, times(1)).sendRegistrationOtp(eq(address), anyString(), anyString(), anyLong());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class, address));
    }

    @Test void generationReplaysWithoutChargingAndRejectsChangedPayloadOrQuotaOverflow() {
        User user = user(); String token = jwt.generateToken(user); UUID key = UUID.randomUUID();
        Result first = call("POST", "/api/posts/generate", token, key, input("A"));
        assertEquals(200, first.status(), first.body().toString());
        Result again = call("POST", "/api/posts/generate", token, key, input("A"));
        assertEquals(first.body(), again.body());
        assertEquals(409, call("POST", "/api/posts/generate", token, key, input("Changed")).status());
        verify(ai, times(1)).generatePost(any(), any(), any());
        assertEquals(200, call("POST", "/api/posts/generate", token, UUID.randomUUID(), input("B")).status());
        assertEquals(429, call("POST", "/api/posts/generate", token, UUID.randomUUID(), input("C")).status());
        assertEquals(2, usage.summary(user.getId()).used());
        assertEquals(0, usage.summary(user.getId()).reserved());
        verify(ai, times(2)).generatePost(any(), any(), any());
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM ai_provider_costs c JOIN ai_usage_requests r ON r.id = c.request_id WHERE r.user_id = ? AND c.status = 'SETTLED'", Integer.class, user.getId()));
        User other = user();
        assertEquals(0, call("GET", "/api/usage/requests", jwt.generateToken(other), null, null).body().path("data").path("total").asInt());
        assertEquals(401, call("GET", "/api/usage", null, null, null).status());
        assertEquals(400, call("POST", "/api/posts/generate", token, null, input("No key")).status());
    }

    @Test void concurrentRequestDoesNotCallProviderTwice() throws Exception {
        User user = user(); String token = jwt.generateToken(user); UUID key = UUID.randomUUID();
        CountDownLatch started = new CountDownLatch(1), finish = new CountDownLatch(1);
        doAnswer(inv -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            started.countDown();
            assertTrue(finish.await(10, TimeUnit.SECONDS));
            Consumer<ProviderTokenUsage> observer = inv.getArgument(2);
            observer.accept(new ProviderTokenUsage(500, 0, 100, "gpt-4o-mini", "fixture-request"));
            return result();
        }).when(ai).generatePost(any(), any(), any());
        var first = CompletableFuture.supplyAsync(() -> call("POST", "/api/posts/generate", token, key, input("Concurrent")));
        try {
            assertTrue(started.await(10, TimeUnit.SECONDS));
            assertEquals(409, call("POST", "/api/posts/generate", token, key, input("Concurrent")).status());
            assertEquals(429, call("POST", "/api/posts/generate", token, UUID.randomUUID(), input("Second" )).status());
        } finally { finish.countDown(); }
        assertEquals(200, first.join().status());
        assertEquals(200, call("POST", "/api/posts/generate", token, key, input("Concurrent")).status());
        verify(ai, times(1)).generatePost(any(), any(), any());
        assertEquals(1, usage.summary(user.getId()).used());
    }

    @Test void providerFailureRefundsQuotaButRetainsCostAttemptsAndKeyCannotRestart() {
        User user = user(); String token = jwt.generateToken(user); UUID key = UUID.randomUUID();
        doThrow(new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Fixture timeout"))
                .when(ai).generatePost(any(), any(), any());
        assertEquals(504, call("POST", "/api/posts/generate", token, key, input("Timeout")).status());
        assertEquals(2, usage.summary(user.getId()).remaining());
        assertEquals("UNKNOWN", usage.history(user.getId(), 0, 10).items().getFirst().status());
        assertEquals(409, call("POST", "/api/posts/generate", token, key, input("Timeout")).status());
        assertEquals(1, jdbc.queryForObject("SELECT sum(attempts) FROM ai_daily_budgets", Integer.class));
        verify(ai, times(1)).generatePost(any(), any(), any());
    }

    @Test void databaseFailureRollsBackPostAndQuotaChargeTogether() {
        User user = user(); String token = jwt.generateToken(user);
        jdbc.execute("CREATE FUNCTION fail_usage_fixture() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.status = 'SUCCEEDED' THEN RAISE EXCEPTION 'Fixture write failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER fail_usage_fixture BEFORE UPDATE ON ai_usage_requests FOR EACH ROW EXECUTE FUNCTION fail_usage_fixture()");
        try {
            assertEquals(500, call("POST", "/api/posts/generate", token, UUID.randomUUID(), input("Rollback")).status());
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM generated_posts WHERE user_id = ?", Integer.class, user.getId()));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM product_briefs WHERE user_id = ?", Integer.class, user.getId()));
            assertEquals(0, usage.summary(user.getId()).used());
            assertEquals(2, usage.summary(user.getId()).remaining());
        } finally {
            jdbc.execute("DROP TRIGGER fail_usage_fixture ON ai_usage_requests");
            jdbc.execute("DROP FUNCTION fail_usage_fixture()");
        }
    }

    @Test void globalBudgetAndAccountQuotaAreAtomicAcrossWorkers() {
        limits.setGlobalDailyAttempts(3);
        List<User> accounts = new ArrayList<>();
        for (int i = 0; i < 8; i++) accounts.add(user());
        List<CompletableFuture<Boolean>> requests = accounts.stream().map(account -> CompletableFuture.supplyAsync(() -> {
            try { usage.reserve(account.getId(), UUID.randomUUID(), "a".repeat(64), "test-model"); return true; }
            catch (RateLimitException expected) { return false; }
        })).toList();
        assertEquals(3, requests.stream().map(CompletableFuture::join).filter(Boolean::booleanValue).count());
        assertEquals(3, jdbc.queryForObject("SELECT sum(attempts) FROM ai_daily_budgets", Integer.class));
        String subject = UUID.randomUUID().toString();
        var rates = java.util.stream.IntStream.range(0, 20).mapToObj(i -> CompletableFuture.supplyAsync(() -> {
            try { rateLimiter.consume("concurrent", subject, 5, 60); return true; }
            catch (RateLimitException expected) { return false; }
        })).toList();
        assertEquals(5, rates.stream().map(CompletableFuture::join).filter(Boolean::booleanValue).count());
    }

    @Test void monetaryBudgetReservationCannotBeExceededByConcurrentWorkers() {
        BigDecimal oldDaily = costProperties.getDailyBudgetUsd();
        BigDecimal oldMonthly = costProperties.getMonthlyBudgetUsd();
        costProperties.setDailyBudgetUsd(new BigDecimal("0.015"));
        costProperties.setMonthlyBudgetUsd(new BigDecimal("0.015"));
        var window = java.time.Instant.parse("2041-01-20T12:00:00Z");
        try {
            List<CompletableFuture<Boolean>> reservations = java.util.stream.IntStream.range(0, 8).mapToObj(index ->
                    CompletableFuture.supplyAsync(() -> {
                        try { costs.reserveBudget(window); return true; }
                        catch (ResponseStatusException expected) { return false; }
                    })).toList();
            assertEquals(1, reservations.stream().map(CompletableFuture::join).filter(Boolean::booleanValue).count());
            assertTrue(jdbc.queryForObject("SELECT reserved_usd FROM ai_cost_budget_windows WHERE window_type = 'DAILY' AND window_start = DATE '2041-01-20'", BigDecimal.class)
                    .compareTo(new BigDecimal("0.015")) < 0);
        } finally {
            costProperties.setDailyBudgetUsd(oldDaily);
            costProperties.setMonthlyBudgetUsd(oldMonthly);
        }
    }

    @Test void globalKillSwitchStopsNewProviderCalls() {
        User user = user();
        jdbc.update("UPDATE ai_runtime_control SET enabled = FALSE WHERE id = 1");
        try {
            assertThrows(ResponseStatusException.class,
                    () -> usage.reserve(user.getId(), UUID.randomUUID(), "f".repeat(64), "fixture-model"));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM ai_usage_requests WHERE user_id = ?", Integer.class, user.getId()));
        } finally {
            jdbc.update("UPDATE ai_runtime_control SET enabled = TRUE WHERE id = 1");
        }
    }

    @Test void expiredReservationIsRefundedOnceAndPeriodSnapshotsSurviveConfigChanges() {
        User user = user(); UUID key = UUID.randomUUID();
        var pending = usage.reserve(user.getId(), key, "b".repeat(64), "fixture-model");
        jdbc.update("UPDATE ai_usage_requests SET created_at = clock_timestamp() - INTERVAL '6 minutes' WHERE id = ?", pending.requestId());
        assertEquals(2, usage.summary(user.getId()).remaining());
        assertEquals(2, usage.summary(user.getId()).remaining());
        assertThrows(AiRequestConflictException.class, () -> usage.reserve(user.getId(), key, "b".repeat(64), "fixture-model"));
        limits.setFreeAiLimit(7);
        assertEquals(2, usage.summary(user.getId()).limit());
        jdbc.update("UPDATE subscription_periods SET starts_at = clock_timestamp() - INTERVAL '60 days', ends_at = clock_timestamp() - INTERVAL '30 days' WHERE user_id = ?", user.getId());
        assertEquals(7, usage.summary(user.getId()).limit());
        assertEquals("FREE", usage.summary(user.getId()).plan());
    }

    @Test void activeProUsesSnapshotAndExpiryFallsBackToFree() {
        User user = user();
        jdbc.update("INSERT INTO subscription_periods (id,user_id,plan_code,starts_at,ends_at,ai_limit,source_reference) VALUES (?,?,'PRO',clock_timestamp()-INTERVAL '1 day',clock_timestamp()+INTERVAL '29 days',17,?)",
                UUID.randomUUID(), user.getId(), "verified-fixture-" + UUID.randomUUID());
        assertEquals("PRO", usage.summary(user.getId()).plan());
        assertEquals(17, usage.summary(user.getId()).limit());
        jdbc.update("UPDATE subscription_periods SET starts_at=clock_timestamp()-INTERVAL '31 days', ends_at=clock_timestamp()-INTERVAL '1 day' WHERE user_id=?", user.getId());
        assertEquals("FREE", usage.summary(user.getId()).plan());
        assertEquals(2, usage.summary(user.getId()).limit());
    }

    @Test void verifiedProProvisioningIsIdempotentAndUsesConfiguredQuota() {
        User user = user();
        var start = java.time.Instant.now().minus(Duration.ofDays(1));
        var end = java.time.Instant.now().plus(Duration.ofDays(29));
        String paymentReference = "verified-payment-" + UUID.randomUUID();

        UUID periodId = subscriptions.activateProFromVerifiedPayment(user.getId(), start, end, paymentReference);
        assertEquals(periodId, subscriptions.activateProFromVerifiedPayment(user.getId(), start, end, paymentReference));
        assertEquals("PRO", usage.summary(user.getId()).plan());
        assertEquals(limits.getProAiLimit(), usage.summary(user.getId()).limit());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM subscription_periods WHERE source_reference = ?", Integer.class, paymentReference));
        assertThrows(ResponseStatusException.class, () -> subscriptions.activateProFromVerifiedPayment(user.getId(), start,
                end, "verified-payment-" + UUID.randomUUID()));
    }

    @Test void dailyAttemptLimitCountsFailedCallsAndInvalidInputDoesNotCallProvider() {
        User user = user(); String token = jwt.generateToken(user);
        limits.setUserDailyAttempts(1);
        assertEquals(422, call("POST", "/api/posts/generate", token, UUID.randomUUID(), input("x".repeat(201))).status());
        Map<String, Object> foreignMedia = new java.util.HashMap<>(input("Invalid media"));
        foreignMedia.put("imageIds", List.of(UUID.randomUUID()));
        assertEquals(400, call("POST", "/api/posts/generate", token, UUID.randomUUID(), foreignMedia).status());
        verifyNoInteractions(ai);
        doThrow(new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Fixture refusal"))
                .when(ai).generatePost(any(), any(), any());
        assertEquals(422, call("POST", "/api/posts/generate", token, UUID.randomUUID(), input("Refused")).status());
        assertEquals(2, usage.summary(user.getId()).remaining());
        assertEquals("FAILED", usage.history(user.getId(), 0, 10).items().getFirst().status());
        assertEquals(429, call("POST", "/api/posts/generate", token, UUID.randomUUID(), input("Another")).status());
        verify(ai, times(1)).generatePost(any(), any(), any());
    }

    @Test void ipLimitCoversInvalidPayloadsBeforeValidationAndIgnoresSpoofedHeaders() {
        for (int i = 0; i < 60; i++) {
            assertEquals(422, call("POST", "/api/auth/login", null, null, Map.of("email", "bad-email")).status());
        }
        assertEquals(429, call("POST", "/api/auth/login", null, null, Map.of("email", "bad-email")).status());
    }

    @Test void expiredOtpIsRemovedAndMailFailureDoesNotExposeRegistrationState() throws Exception {
        String failedAddress = UUID.randomUUID() + "@example.test";
        Map<String, Object> failedRegistration = Map.of("fullName", "Mail failure fixture", "email", failedAddress, "password", PASSWORD);
        doThrow(new IllegalStateException("SMTP fixture unavailable")).when(email).sendRegistrationOtp(eq(failedAddress), anyString(), anyString(), anyLong());
        assertEquals(202, call("POST", "/api/auth/register", null, null, failedRegistration).status());
        verify(email, timeout(5_000)).sendRegistrationOtp(eq(failedAddress), anyString(), anyString(), anyLong());
        awaitPendingCount(failedAddress, 0);
        assertEquals(429, call("POST", "/api/auth/register", null, null, failedRegistration).status());

        String expiredAddress = UUID.randomUUID() + "@example.test";
        AtomicReference<String> otp = new AtomicReference<>();
        doAnswer(inv -> { otp.set(inv.getArgument(2)); return null; })
                .when(email).sendRegistrationOtp(eq(expiredAddress), anyString(), anyString(), anyLong());
        Map<String, Object> registration = Map.of("fullName", "Expiry fixture", "email", expiredAddress, "password", PASSWORD);
        assertEquals(202, call("POST", "/api/auth/register", null, null, registration).status());
        verify(email, timeout(5_000)).sendRegistrationOtp(eq(expiredAddress), anyString(), anyString(), anyLong());
        jdbc.update("UPDATE pending_registrations SET otp_expires_at = TIMESTAMP '2000-01-01' WHERE email = ?", expiredAddress);
        assertEquals(401, call("POST", "/api/auth/register/verify", null, null, Map.of("email", expiredAddress, "code", "123456")).status());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM pending_registrations WHERE email = ?", Integer.class, expiredAddress));
        assertFalse(users.existsByEmailIgnoreCase(expiredAddress));
    }

    private void awaitPendingCount(String email, int expected) throws InterruptedException {
        for (int attempt = 0; attempt < 50; attempt++) {
            int count = jdbc.queryForObject("SELECT count(*) FROM pending_registrations WHERE email = ?", Integer.class, email);
            if (count == expected) return;
            Thread.sleep(100);
        }
        assertEquals(expected, jdbc.queryForObject("SELECT count(*) FROM pending_registrations WHERE email = ?", Integer.class, email));
    }

    private User user() {
        User user = new User(); user.setFullName("Safety fixture"); user.setEmail(UUID.randomUUID() + "@example.test");
        user.setPasswordHash(passwords.encode(PASSWORD)); return users.saveAndFlush(user);
    }
    private Map<String, Object> input(String name) {
        return Map.of("productName", name, "productDescription", "Description", "targetAudience", "Audience", "highlights", "Benefits",
                "goal", "Goal", "tone", "Friendly", "platforms", List.of("FACEBOOK"));
    }
    private AiPostResult result() {
        return new AiPostResult("Title", Map.of("FACEBOOK", Map.of("content", "Fixture generated content")), List.of("#fixture"), "CTA", "Image suggestion");
    }
    private Result call(String method, String path, String token, UUID key, Object body) {
        try {
            var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json").header("X-Real-IP", "198.51.100." + (1 + (int)(Math.random() * 200)));
            if (token != null) builder.header("Authorization", "Bearer " + token);
            if (key != null) builder.header("Idempotency-Key", key.toString());
            builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new Result(response.statusCode(), json.readTree(response.body()), response.headers().firstValue("Retry-After").orElse("0"));
        } catch (Exception e) { throw new AssertionError("HTTP fixture failed", e); }
    }
    private record Result(int status, JsonNode body, String retryAfter) { }
}
