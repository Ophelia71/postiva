package com.postiva.analytics;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Runs only against the dedicated local test database; never reads application secrets. */
@EnabledIfEnvironmentVariable(named = "POSTIVA_TEST_DB_URL", matches = ".+")
class WebAnalyticsIntegrationTest {
    private static final String URL = System.getenv("POSTIVA_TEST_DB_URL");
    private static final String USER = System.getenv().getOrDefault("POSTIVA_TEST_DB_USERNAME", "postgres");
    private static final String PASSWORD = System.getenv().getOrDefault("POSTIVA_TEST_DB_PASSWORD", "");
    private static final String SCHEMA = "analytics_test_" + UUID.randomUUID().toString().replace("-", "");
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void prepare() throws Exception {
        assertTrue(URL.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/postiva_migration_test(\\?.*)?"));
        try (var connection = DriverManager.getConnection(URL, USER, PASSWORD);
             var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + SCHEMA);
        }
        Flyway.configure().dataSource(URL, USER, PASSWORD).schemas(SCHEMA).defaultSchema(SCHEMA)
                .locations("classpath:db/migration")
                .placeholders(Map.of("legacyScheduleTimeZone", "UTC", "legacyTimestampTimeZone", "UTC"))
                .load().migrate();
        var source = new DriverManagerDataSource(URL + (URL.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA,
                USER, PASSWORD);
        jdbc = new JdbcTemplate(source);
    }

    @AfterAll
    static void cleanup() throws Exception {
        if (URL == null || !URL.contains("/postiva_migration_test")) return;
        try (var connection = DriverManager.getConnection(URL, USER, PASSWORD);
             var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
    }

    @Test
    void onboardingMilestonesAreStoredButDoNotInflateSessionOrScrollMetrics() {
        WebAnalyticsService tracker = new WebAnalyticsService(jdbc);
        AdminWebAnalyticsService reports = new AdminWebAnalyticsService(jdbc);
        UUID visitor = UUID.randomUUID(), session = UUID.randomUUID();
        long sessionsBefore = reports.dashboard(7).sessions().sessions();
        long viewsBefore = reports.dashboard(7).sessions().pageViews();
        long scrollViewsBefore = reports.dashboard(7).scroll().views();
        UUID orphanSession = UUID.randomUUID();
        try {
        // A milestone without any real page view must not start a session.
        tracker.record(new WebAnalyticsService.Event(WebAnalyticsService.Type.PAGE_VIEW, visitor, orphanSession,
                UUID.randomUUID(), "/virtual/onboarding/step/connect", WebAnalyticsService.Source.DIRECT, null), null);
        assertEquals(sessionsBefore, reports.dashboard(7).sessions().sessions());

        tracker.record(new WebAnalyticsService.Event(WebAnalyticsService.Type.PAGE_VIEW, visitor, session,
                UUID.randomUUID(), "/app/start", WebAnalyticsService.Source.DIRECT, null), null);
        UUID milestone = UUID.randomUUID();
        var event = new WebAnalyticsService.Event(WebAnalyticsService.Type.PAGE_VIEW, visitor, session,
                milestone, "/virtual/onboarding/step/connect", WebAnalyticsService.Source.DIRECT, null);
        tracker.record(event, null);
        tracker.record(event, null);

        var report = reports.dashboard(7);
        assertEquals(sessionsBefore + 1, report.sessions().sessions());
        assertEquals(viewsBefore + 1, report.sessions().pageViews());
        assertEquals(scrollViewsBefore + 1, report.scroll().views());
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM web_analytics_page_views WHERE path = '/virtual/onboarding/step/connect'", Integer.class));
        } finally {
            // The other test in this class counts sessions, so leave nothing behind.
            jdbc.update("DELETE FROM web_analytics_sessions WHERE id IN (?, ?)", session, orphanSession);
        }
    }

    @Test
    void deduplicatesPageViewsAndReportsBehaviourTrafficAndVerifiedConversions() {
        WebAnalyticsService tracker = new WebAnalyticsService(jdbc);
        AdminWebAnalyticsService reports = new AdminWebAnalyticsService(jdbc);
        UUID visitor = UUID.randomUUID(), session = UUID.randomUUID(), view = UUID.randomUUID();
        var page = new WebAnalyticsService.Event(WebAnalyticsService.Type.PAGE_VIEW, visitor, session,
                view, "/", WebAnalyticsService.Source.DIRECT, null);
        tracker.record(page, null);
        tracker.record(page, null);
        var first = reports.dashboard(7);
        assertEquals(1, first.sessions().sessions());
        assertEquals(1, first.sessions().pageViews());
        assertEquals(1, first.sessions().visitors());
        assertEquals(100.0, first.bounceRatePercent());
        assertEquals(1, first.visitorMix().newVisitors());

        tracker.record(new WebAnalyticsService.Event(WebAnalyticsService.Type.SCROLL, visitor, session,
                view, null, null, 50), null);
        var engaged = reports.dashboard(7);
        assertEquals(0.0, engaged.bounceRatePercent());
        assertEquals(1, engaged.scroll().depth25());
        assertEquals(1, engaged.scroll().depth50());
        assertEquals(0, engaged.scroll().depth75());

        UUID account = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, full_name, email, password_hash) VALUES (?, ?, ?, ?)",
                account, "Analytics Test", "analytics-" + account + "@example.test", "test-hash");
        tracker.record(new WebAnalyticsService.Event(WebAnalyticsService.Type.IDENTIFY, visitor, session,
                null, null, null, null), account);
        var registered = reports.dashboard(7);
        assertEquals(1, registered.registrations());
        assertEquals(1, registered.attributedRegistrations());
        assertEquals(100.0, registered.signUpRatePercent());

        jdbc.update("""
                INSERT INTO payment_orders (id, user_id, idempotency_key, amount_vnd, ai_limit, period_days,
                                            status, provider_transaction_ref, paid_at, expires_at)
                VALUES (?, ?, ?, 99000, 100, 30, 'PAID', ?, ?, ?)
                """, UUID.randomUUID(), account, UUID.randomUUID(), "TEST-" + account,
                Timestamp.from(Instant.now()), Timestamp.from(Instant.now().plusSeconds(3600)));
        var paid = reports.dashboard(7);
        assertEquals(1, paid.payments().orders());
        assertEquals(1, paid.payments().buyers());
        assertEquals(99000, paid.payments().grossVnd());
        assertEquals(100.0, paid.purchaseRatePercent());
        assertEquals(99000.0, paid.averageOrderValueVnd());

        tracker.excludeAdminSession(new WebAnalyticsService.Event(WebAnalyticsService.Type.EXCLUDE,
                visitor, session, null, null, null, null));
        assertEquals(0, reports.dashboard(7).sessions().sessions());
        assertEquals(1, reports.dashboard(7).registrations());
    }
}
