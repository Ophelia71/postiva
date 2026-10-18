package com.postiva.scheduler.service;

import com.postiva.scheduler.PublishingScheduler;
import com.postiva.scheduler.service.ScheduledPostStateService.Claim;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Real PostgreSQL coverage for SKIP LOCKED and expired-lease recovery. */
@EnabledIfEnvironmentVariable(named = "POSTIVA_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = {
        "spring.config.import=", "postiva.mail.enabled=false",
        "postiva.openai.api-key=scheduler-test-no-provider-calls",
        "postiva.storage.upload-dir=target/scheduler-test-uploads",
        "postiva.admin.bootstrap.enabled=false",
        "postiva.scheduler.publish-delay-ms=3600000"
})
class ScheduledPostClaimIntegrationTest {
    private static final String SCHEMA = "scheduler_claim_" + UUID.randomUUID().toString().replace("-", "");
    private static final String URL = System.getenv("POSTIVA_TEST_DB_URL");
    private static final String DB_USER = System.getenv().getOrDefault("POSTIVA_TEST_DB_USERNAME", "postgres");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("POSTIVA_TEST_DB_PASSWORD", "");

    @Autowired JdbcTemplate jdbc;
    @Autowired ScheduledPostClaimService claims;
    @MockBean PublishingScheduler scheduler;

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

    @Test
    void competingWorkersCanClaimEachDueScheduleOnlyOnce() throws Exception {
        UUID scheduledId = createDueSchedule();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> claimAtSameTime(ready, start));
            var second = pool.submit(() -> claimAtSameTime(ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            List<Claim> result = new ArrayList<>(first.get(10, TimeUnit.SECONDS));
            result.addAll(second.get(10, TimeUnit.SECONDS));

            assertEquals(1, result.size());
            assertEquals(scheduledId, result.getFirst().scheduledPostId());
            assertEquals(1, jdbc.queryForObject(
                    "SELECT attempt_count FROM scheduled_posts WHERE id = ?", Integer.class, scheduledId));
            assertEquals("PUBLISHING", jdbc.queryForObject(
                    "SELECT status FROM scheduled_posts WHERE id = ?", String.class, scheduledId));
        }
    }

    @Test
    void expiredLeaseAfterWorkerRestartBecomesUnknownAndIsNeverAutoClaimed() {
        UUID scheduledId = createDueSchedule();
        List<Claim> firstClaim = claims.claimDue(Instant.now(), 1, Duration.ofMinutes(5));
        assertEquals(1, firstClaim.size());
        jdbc.update("UPDATE scheduled_posts SET lease_until = ? WHERE id = ?",
                Timestamp.from(Instant.now().minusSeconds(1)), scheduledId);

        List<Claim> afterRestart = claims.claimDue(Instant.now(), 1, Duration.ofMinutes(5));

        assertTrue(afterRestart.isEmpty());
        assertEquals("UNKNOWN", jdbc.queryForObject(
                "SELECT status FROM scheduled_posts WHERE id = ?", String.class, scheduledId));
        assertNull(jdbc.queryForObject(
                "SELECT claim_token FROM scheduled_posts WHERE id = ?", UUID.class, scheduledId));
        assertTrue(jdbc.queryForObject(
                "SELECT last_error FROM scheduled_posts WHERE id = ?", String.class, scheduledId)
                .toLowerCase(Locale.ROOT).contains("kết quả đăng chưa xác định"));
        assertEquals(1, jdbc.queryForObject(
                "SELECT attempt_count FROM scheduled_posts WHERE id = ?", Integer.class, scheduledId));
    }

    private List<Claim> claimAtSameTime(CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS));
        return claims.claimDue(Instant.now(), 1, Duration.ofMinutes(5));
    }

    private UUID createDueSchedule() {
        UUID userId = UUID.randomUUID();
        UUID briefId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, full_name, email, password_hash) VALUES (?, 'Scheduler test', ?, 'hash')",
                userId, userId + "@scheduler-test.example");
        jdbc.update("INSERT INTO product_briefs(id, user_id, product_name) VALUES (?, ?, 'Schedule fixture')",
                briefId, userId);
        jdbc.update("INSERT INTO generated_posts(id, brief_id, user_id, status) VALUES (?, ?, ?, 'SCHEDULED')",
                postId, briefId, userId);
        jdbc.update("""
                INSERT INTO scheduled_posts(id, post_id, platform, scheduled_time, status)
                VALUES (?, ?, 'FACEBOOK', ?, 'SCHEDULED')
                """, scheduleId, postId, Timestamp.from(Instant.now().minusSeconds(60)));
        return scheduleId;
    }
}
