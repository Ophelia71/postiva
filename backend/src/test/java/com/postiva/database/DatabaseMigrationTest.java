package com.postiva.database;

import com.postiva.admin.entity.AdminAuditLog;
import com.postiva.admin.entity.AdminSecurityEvent;
import com.postiva.admin.entity.AdminMfaState;
import com.postiva.admin.repository.AdminAuditLogRepository;
import com.postiva.admin.service.AdminUserService;
import com.postiva.auth.entity.PendingRegistration;
import com.postiva.auth.entity.PasswordResetToken;
import com.postiva.auth.service.CurrentUserService;
import com.postiva.business.entity.BusinessProfile;
import com.postiva.calendar.entity.CalendarItem;
import com.postiva.calendar.entity.ContentCalendar;
import com.postiva.file.entity.UploadedFile;
import com.postiva.post.entity.GeneratedPost;
import com.postiva.post.entity.ProductBrief;
import com.postiva.scheduler.entity.PublishLog;
import com.postiva.scheduler.entity.ScheduledPost;
import com.postiva.scheduler.service.PublishingService;
import com.postiva.scoring.entity.PostScore;
import com.postiva.social.entity.SocialAccount;
import com.postiva.social.entity.SocialDataDeletionRequest;
import com.postiva.template.entity.IndustryTemplate;
import com.postiva.user.entity.User;
import com.postiva.user.repository.UserRepository;
import com.postiva.user.service.UserAccountStatusService;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.hibernate.cfg.Configuration;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Opt-in, real PostgreSQL tests. Never reads the application's .env/database. */
@EnabledIfEnvironmentVariable(named = "POSTIVA_TEST_DB_URL", matches = ".+")
class DatabaseMigrationTest {
    private static final String ADMIN = "00000000-0000-0000-0000-000000000001";
    private static final String USER = "00000000-0000-0000-0000-000000000002";
    private final String url = System.getenv("POSTIVA_TEST_DB_URL");
    private final String username = System.getenv().getOrDefault("POSTIVA_TEST_DB_USERNAME", "postgres");
    private final String password = System.getenv().getOrDefault("POSTIVA_TEST_DB_PASSWORD", "");
    private String schema;

    @BeforeEach
    void setUp() {
        assertTrue(url.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/postiva_migration_test(\\?.*)?"),
                "Tests require a dedicated, local postiva_migration_test database");
        schema = "migration_test_" + UUID.randomUUID().toString().replace("-", "");
    }

    @AfterEach
    void cleanUp() throws SQLException {
        if (schema != null) {
            execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    void emptyDatabaseMigratesAndValidatesAllApplicationEntities() {
        assertEquals(12, flyway(null).migrate().migrationsExecuted);
        assertEquals("12", flyway(null).info().current().getVersion().getVersion());
        assertTrue(flyway(null).validateWithResult().validationSuccessful);
        validateEntities();
    }

    @Test
    void explicitBaselinePreservesExistingUsersAndContentAcrossMigrationAndRestart() throws SQLException {
        // Reconstruct the pre-Flyway shape, then remove only this test schema's
        // history to model an existing application database without Flyway.
        flyway("1").migrate();
        seedExistingData();
        Map<String, String> original = snapshot();
        execute("DROP TABLE " + schema + ".flyway_schema_history");

        assertThrows(FlywayException.class, () -> flyway(null).migrate());
        assertEquals(original, snapshot());
        flyway(null).baseline();
        assertEquals(11, flyway(null).migrate().migrationsExecuted);
        assertEquals(original, snapshot());
        assertEquals("ACTIVE", query("SELECT status FROM " + schema + ".users WHERE id = '" + USER + "'"));
        assertEquals("0", query("SELECT count(*) FROM " + schema + ".admin_audit_logs"));
        assertEquals("0", query("SELECT auth_version FROM " + schema + ".users WHERE id = '" + USER + "'"));

        execute("UPDATE " + schema + ".users SET status = 'LOCKED', locked_at = CURRENT_TIMESTAMP, "
                + "locked_by = '" + ADMIN + "', lock_reason = 'Existing lock' WHERE id = '" + USER + "'");
        assertEquals(0, flyway(null).migrate().migrationsExecuted);
        assertEquals("LOCKED", query("SELECT status FROM " + schema + ".users WHERE id = '" + USER + "'"));
        assertEquals(original, snapshot());
        validateEntities();
    }

    @Test
    void invalidStatusAndAuditMutationAreRejectedByDatabase() throws SQLException {
        flyway(null).migrate();
        seedExistingData();
        assertThrows(SQLException.class, () -> execute("UPDATE " + schema + ".users SET status = 'OTHER'"));
        assertThrows(SQLException.class, () -> execute("UPDATE " + schema + ".users SET status = 'LOCKED'"));
        execute("INSERT INTO " + schema + ".admin_audit_logs "
                + "(actor_user_id, target_user_id, action, previous_status, new_status, reason) VALUES "
                + "('" + ADMIN + "', '" + USER + "', 'LOCK_USER', 'ACTIVE', 'LOCKED', 'Audit fixture')");
        SQLException update = assertThrows(SQLException.class,
                () -> execute("UPDATE " + schema + ".admin_audit_logs SET reason = 'Changed'"));
        assertEquals("55000", update.getSQLState());
        assertThrows(SQLException.class, () -> execute("DELETE FROM " + schema + ".admin_audit_logs"));
        assertThrows(SQLException.class, () -> execute("TRUNCATE " + schema + ".admin_audit_logs"));
        execute("INSERT INTO " + schema + ".admin_security_events (admin_user_id, action) VALUES "
                + "('" + ADMIN + "', 'PASSWORD_CHANGED')");
        assertThrows(SQLException.class, () -> execute("DELETE FROM " + schema + ".admin_security_events"));
        assertThrows(SQLException.class, () -> execute("TRUNCATE " + schema + ".admin_security_events"));
        assertThrows(SQLException.class, () -> execute("DELETE FROM " + schema + ".users WHERE id = '" + ADMIN + "'"));
        assertEquals("Audit fixture", query("SELECT reason FROM " + schema + ".admin_audit_logs"));
    }

    @Test
    void v5PreservesPendingRegistrationAndAddsEmptyUsageTables() throws SQLException {
        flyway("4").migrate();
        execute("INSERT INTO " + schema + ".pending_registrations (full_name,email,password_hash,otp_hash,otp_expires_at) VALUES "
                + "('Pending fixture','pending@example.test','original-password-hash','original-otp-hash',TIMESTAMP '2030-01-01')");
        String before = query("SELECT (to_jsonb(p))::text FROM " + schema + ".pending_registrations p");
        assertEquals(8, flyway(null).migrate().migrationsExecuted);
        assertEquals(before, query("SELECT (to_jsonb(p) - 'failed_attempts')::text FROM " + schema + ".pending_registrations p"));
        assertEquals("0", query("SELECT failed_attempts FROM " + schema + ".pending_registrations"));
        assertEquals("0", query("SELECT count(*) FROM " + schema + ".subscription_periods"));
        assertEquals("0", query("SELECT count(*) FROM " + schema + ".ai_usage_requests"));
        assertEquals("0", query("SELECT count(*) FROM " + schema + ".request_rate_limits"));
        assertEquals("0", query("SELECT count(*) FROM " + schema + ".password_reset_tokens"));
        assertEquals("0", query("SELECT count(*) FROM " + schema + ".ai_cost_budget_windows"));
        assertEquals("0", query("SELECT count(*) FROM " + schema + ".ai_provider_costs"));
        assertEquals("integer", query("SELECT data_type FROM information_schema.columns WHERE table_schema = '" + schema + "' AND table_name = 'ai_provider_costs' AND column_name = 'cache_write_input_tokens'"));
        assertEquals("1", query("SELECT count(*) FROM " + schema + ".ai_runtime_control WHERE enabled"));
    }

    @Test
    void v9AddsCacheWriteAccountingWithoutChangingExistingCostReservations() throws SQLException {
        flyway("8").migrate();
        seedExistingData();
        String periodId = "00000000-0000-0000-0000-000000000020";
        String requestId = "00000000-0000-0000-0000-000000000021";
        execute("INSERT INTO " + schema + ".subscription_periods (id, user_id, plan_code, starts_at, ends_at, ai_limit) VALUES "
                + "('" + periodId + "', '" + USER + "', 'FREE', TIMESTAMPTZ '2026-10-17 00:00:00+00', "
                + "TIMESTAMPTZ '2026-11-16 00:00:00+00', 5)");
        execute("INSERT INTO " + schema + ".ai_usage_requests (id, user_id, period_id, idempotency_key, request_hash, status, model) VALUES "
                + "('" + requestId + "', '" + USER + "', '" + periodId + "', '00000000-0000-0000-0000-000000000022', "
                + "'0000000000000000000000000000000000000000000000000000000000000000', 'RESERVED', 'gpt-4o-mini')");
        execute("INSERT INTO " + schema + ".ai_provider_costs (request_id, usage_day, usage_month, model, "
                + "input_rate_usd_per_million, cached_input_rate_usd_per_million, output_rate_usd_per_million, status, reserved_usd) VALUES "
                + "('" + requestId + "', DATE '2026-10-17', DATE '2026-10-01', 'gpt-4o-mini', 0.15, 0.075, 0.60, 'RESERVED', 0.01)");
        String before = query("SELECT to_jsonb(cost)::text FROM " + schema + ".ai_provider_costs cost WHERE request_id = '" + requestId + "'");

        assertEquals(4, flyway(null).migrate().migrationsExecuted);
        assertEquals(before, query("SELECT (to_jsonb(cost) - ARRAY['cache_write_input_rate_usd_per_million', "
                + "'cache_write_input_tokens'])::text FROM " + schema + ".ai_provider_costs cost WHERE request_id = '" + requestId + "'"));
        assertEquals("0.0000000000", query("SELECT cache_write_input_rate_usd_per_million::text FROM " + schema
                + ".ai_provider_costs WHERE request_id = '" + requestId + "'"));
        assertEquals("t", query("SELECT cache_write_input_tokens IS NULL FROM " + schema
                + ".ai_provider_costs WHERE request_id = '" + requestId + "'"));
    }

    @Test
    void v11RaisesActiveFreeTrialQuotaAndPreservesUsage() throws SQLException {
        flyway("10").migrate();
        seedExistingData();
        String activePeriodId = "00000000-0000-0000-0000-000000000030";
        String expiredPeriodId = "00000000-0000-0000-0000-000000000031";
        execute("INSERT INTO " + schema + ".subscription_periods (id, user_id, plan_code, starts_at, ends_at, ai_limit, ai_used) VALUES "
                + "('" + activePeriodId + "', '" + USER + "', 'FREE', CURRENT_TIMESTAMP - INTERVAL '1 day', "
                + "CURRENT_TIMESTAMP + INTERVAL '29 days', 5, 3),"
                + "('" + expiredPeriodId + "', '" + USER + "', 'FREE', CURRENT_TIMESTAMP - INTERVAL '40 days', "
                + "CURRENT_TIMESTAMP - INTERVAL '10 days', 5, 5)");

        assertEquals(2, flyway(null).migrate().migrationsExecuted);
        assertEquals("30", query("SELECT ai_limit FROM " + schema + ".subscription_periods WHERE id = '" + activePeriodId + "'"));
        assertEquals("3", query("SELECT ai_used FROM " + schema + ".subscription_periods WHERE id = '" + activePeriodId + "'"));
        assertEquals("5", query("SELECT ai_limit FROM " + schema + ".subscription_periods WHERE id = '" + expiredPeriodId + "'"));
    }

    @Test
    void v6PreservesLegacyUtcScheduleTimeAndQuarantinesOldPublishingRows() throws SQLException {
        flyway("5").migrate();
        seedExistingData();
        String postId = query("SELECT id FROM " + schema + ".generated_posts WHERE brief_id = '00000000-0000-0000-0000-000000000003'");
        execute("INSERT INTO " + schema + ".scheduled_posts "
                + "(post_id, platform, scheduled_time, published_at, created_at, updated_at, status) VALUES "
                + "('" + postId + "', 'FACEBOOK', TIMESTAMP '2026-10-20 15:30:00', "
                + "TIMESTAMP '2026-10-19 10:15:00', TIMESTAMP '2026-10-18 09:00:00', "
                + "TIMESTAMP '2026-10-18 09:30:00', 'PUBLISHING')");

        assertEquals(7, flyway(null, "UTC", "Asia/Ho_Chi_Minh").migrate().migrationsExecuted);

        assertEquals("UNKNOWN", query("SELECT status FROM " + schema + ".scheduled_posts"));
        assertEquals("2026-10-20 15:30:00", query("SELECT to_char(scheduled_time AT TIME ZONE 'UTC', 'YYYY-MM-DD HH24:MI:SS') FROM " + schema + ".scheduled_posts"));
        assertEquals("2026-10-18 02:00:00", query("SELECT to_char(created_at AT TIME ZONE 'UTC', 'YYYY-MM-DD HH24:MI:SS') FROM " + schema + ".scheduled_posts"));
        assertEquals("2026-10-19 03:15:00", query("SELECT to_char(published_at AT TIME ZONE 'UTC', 'YYYY-MM-DD HH24:MI:SS') FROM " + schema + ".scheduled_posts"));
        assertEquals("timestamp with time zone", query("SELECT data_type FROM information_schema.columns WHERE table_schema = '" + schema + "' AND table_name = 'scheduled_posts' AND column_name = 'scheduled_time'"));
        assertEquals("0", query("SELECT count(*) FROM " + schema + ".scheduled_posts WHERE claim_token IS NOT NULL OR lease_until IS NOT NULL"));
        assertEquals("1", query("SELECT count(*) FROM " + schema + ".publish_logs WHERE status = 'UNKNOWN'"));
    }

    @Test
    void accountStatusAndAuditCommitOrRollBackTogether() throws SQLException {
        flyway(null).migrate();
        seedExistingData();
        try (SessionFactory sessionFactory = sessionFactory()) {
            var entityManager = SharedEntityManagerCreator.createSharedEntityManager(sessionFactory);
            var repositories = new JpaRepositoryFactory(entityManager);
            UserRepository users = repositories.getRepository(UserRepository.class);
            AdminAuditLogRepository audit = repositories.getRepository(AdminAuditLogRepository.class);
            CurrentUserService currentUser = mock(CurrentUserService.class);
            when(currentUser.requireCurrentUser()).thenReturn(entityManager.find(User.class, UUID.fromString(ADMIN)));
            PublishingService publishing = mock(PublishingService.class);

            AdminAuditLogRepository failingAudit = mock(AdminAuditLogRepository.class);
            when(failingAudit.save(any())).thenThrow(new DataIntegrityViolationException("Audit write failed"));
            UserAccountStatusService failing = transactional(new UserAccountStatusService(
                    users, failingAudit, currentUser, publishing), sessionFactory);
            assertThrows(DataIntegrityViolationException.class,
                    () -> failing.lockUser(UUID.fromString(USER), "Must roll back"));
            assertEquals("ACTIVE", query("SELECT status FROM " + schema + ".users WHERE id = '" + USER + "'"));
            assertEquals("0", query("SELECT count(*) FROM " + schema + ".admin_audit_logs"));

            UserAccountStatusService service = transactional(new UserAccountStatusService(
                    users, audit, currentUser, publishing), sessionFactory);
            service.lockUser(UUID.fromString(USER), "Verified lock");
            assertEquals("LOCKED", query("SELECT status FROM " + schema + ".users WHERE id = '" + USER + "'"));
            assertEquals("1", query("SELECT count(*) FROM " + schema + ".admin_audit_logs"));
            service.unlockUser(UUID.fromString(USER), "Verified unlock");
            assertEquals("ACTIVE", query("SELECT status FROM " + schema + ".users WHERE id = '" + USER + "'"));
            assertEquals("2", query("SELECT count(*) FROM " + schema + ".admin_audit_logs"));
            assertEquals("Verified lock", query("SELECT reason FROM " + schema
                    + ".admin_audit_logs WHERE action = 'LOCK_USER'"));
        }
    }

    @Test
    void adminSearchPaginationAndAuditUseRealUserOnlyQueries() throws SQLException {
        flyway(null).migrate();
        seedExistingData();
        execute("INSERT INTO " + schema + ".users (full_name, email, password_hash) VALUES "
                + "('Sale 100%_off', 'offer@example.test', 'fixture-hash')");
        try (SessionFactory sessionFactory = sessionFactory()) {
            var entityManager = SharedEntityManagerCreator.createSharedEntityManager(sessionFactory);
            var repositories = new JpaRepositoryFactory(entityManager);
            UserRepository users = repositories.getRepository(UserRepository.class);
            AdminAuditLogRepository audit = repositories.getRepository(AdminAuditLogRepository.class);
            CurrentUserService currentUser = mock(CurrentUserService.class);
            when(currentUser.requireCurrentUser()).thenReturn(entityManager.find(User.class, UUID.fromString(ADMIN)));
            AdminUserService queryService = new AdminUserService(users, audit, currentUser);

            assertEquals(2, queryService.list("", null, 0, 1).totalElements());
            assertEquals(1, queryService.list("", null, 1, 1).items().size());
            assertEquals(1, queryService.list("USER@EXAMPLE", null, 0, 20).totalElements());
            assertEquals(1, queryService.list("%_", null, 0, 20).totalElements());
            assertEquals(0, queryService.list("missing", null, 0, 20).totalElements());
            assertThrows(ResponseStatusException.class, () -> queryService.detail(UUID.fromString(ADMIN)));
            assertThrows(ResponseStatusException.class, () -> queryService.audit(UUID.fromString(ADMIN), 0, 20));

            var statuses = transactional(new UserAccountStatusService(users, audit, currentUser,
                    mock(PublishingService.class)), sessionFactory);
            statuses.lockUser(UUID.fromString(USER), "Search status fixture");
            assertEquals(1, queryService.list("", User.Status.LOCKED, 0, 20).totalElements());
            assertEquals(1, queryService.list("", User.Status.ACTIVE, 0, 20).totalElements());
            var entries = queryService.audit(UUID.fromString(USER), 0, 10);
            assertEquals(1, entries.totalElements());
            assertEquals("Admin fixture", entries.items().getFirst().actorName());
            assertEquals("Search status fixture", entries.items().getFirst().reason());
        }
    }

    private Flyway flyway(String target) {
        return flyway(target,
                System.getenv().getOrDefault("POSTIVA_LEGACY_SCHEDULE_TIME_ZONE", "UTC"),
                System.getenv().getOrDefault("POSTIVA_LEGACY_TIMESTAMP_TIME_ZONE", "UTC"));
    }

    private Flyway flyway(String target, String scheduleTimeZone, String timestampTimeZone) {
        var configuration = Flyway.configure().dataSource(url, username, password)
                .schemas(schema).defaultSchema(schema).locations("classpath:db/migration")
                .baselineVersion("1").baselineOnMigrate(false).cleanDisabled(true)
                .placeholders(Map.of(
                        "legacyScheduleTimeZone", scheduleTimeZone,
                        "legacyTimestampTimeZone", timestampTimeZone
                ));
        if (target != null) {
            configuration.target(target);
        }
        return configuration.load();
    }

    private void seedExistingData() throws SQLException {
        execute("INSERT INTO " + schema + ".users (id, full_name, email, password_hash, role) VALUES "
                + "('" + ADMIN + "', 'Admin fixture', 'admin@example.test', 'unchanged-admin-hash', 'ADMIN'),"
                + "('" + USER + "', 'Người dùng cũ', 'user@example.test', 'unchanged-user-hash', 'USER')");
        execute("INSERT INTO " + schema + ".product_briefs (id, user_id, product_name, additional_info) VALUES "
                + "('00000000-0000-0000-0000-000000000003', '" + USER + "', 'Sản phẩm cũ', 'Preserve notes')");
        execute("INSERT INTO " + schema + ".generated_posts (brief_id, user_id, platform_contents, hashtags) VALUES "
                + "('00000000-0000-0000-0000-000000000003', '" + USER + "', "
                + "'{\"FACEBOOK\":{\"content\":\"Nội dung đã lưu\"}}'::jsonb, '[\"#existing\"]'::jsonb)");
        execute("INSERT INTO " + schema + ".social_accounts (user_id, provider, access_token_encrypted) VALUES "
                + "('" + USER + "', 'FACEBOOK', 'unchanged-fixture-ciphertext')");
    }

    private Map<String, String> snapshot() throws SQLException {
        Map<String, String> snapshot = new LinkedHashMap<>();
        // Project exactly the original user columns: newly added lock metadata
        // must not obscure changes to pre-existing identity/password/timestamps.
        snapshot.put("users", query("SELECT jsonb_agg(to_jsonb(original) ORDER BY id)::text FROM "
                + "(SELECT id, full_name, email, password_hash, role, created_at, updated_at FROM "
                + schema + ".users) original"));
        for (String table : new String[]{"product_briefs", "generated_posts", "social_accounts", "industry_templates"}) {
            snapshot.put(table, query("SELECT coalesce(jsonb_agg(to_jsonb(original) ORDER BY id)::text, '[]') "
                    + "FROM " + schema + "." + table + " original"));
        }
        return snapshot;
    }

    private void validateEntities() {
        try (var sessionFactory = sessionFactory()) {
            assertTrue(sessionFactory.isOpen());
        }
    }

    private SessionFactory sessionFactory() {
        Configuration configuration = new Configuration();
        for (Class<?> entity : new Class<?>[]{User.class, AdminAuditLog.class, AdminSecurityEvent.class, AdminMfaState.class, PendingRegistration.class, PasswordResetToken.class,
                BusinessProfile.class, CalendarItem.class, ContentCalendar.class, UploadedFile.class,
                GeneratedPost.class, ProductBrief.class, PublishLog.class, ScheduledPost.class, PostScore.class,
                SocialAccount.class, SocialDataDeletionRequest.class, IndustryTemplate.class}) {
            configuration.addAnnotatedClass(entity);
        }
        configuration.setProperty("hibernate.connection.url", url);
        configuration.setProperty("hibernate.connection.username", username);
        configuration.setProperty("hibernate.connection.password", password);
        configuration.setProperty("hibernate.default_schema", schema);
        configuration.setPhysicalNamingStrategy(
                new org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy());
        configuration.setProperty("hibernate.hbm2ddl.auto", "validate");
        return configuration.buildSessionFactory();
    }

    private UserAccountStatusService transactional(UserAccountStatusService service, SessionFactory sessionFactory) {
        ProxyFactory proxy = new ProxyFactory(service);
        proxy.addAdvice(new TransactionInterceptor(new JpaTransactionManager(sessionFactory),
                new AnnotationTransactionAttributeSource()));
        return (UserAccountStatusService) proxy.getProxy();
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private String query(String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }
}
