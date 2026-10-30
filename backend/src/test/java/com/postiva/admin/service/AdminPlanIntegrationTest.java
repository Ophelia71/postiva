package com.postiva.admin.service;

import com.postiva.ai.service.OpenAiService;
import com.postiva.auth.service.CurrentUserService;
import com.postiva.mail.EmailService;
import com.postiva.scheduler.PublishingScheduler;
import com.postiva.usage.AiUsageService;
import com.postiva.usage.UsageProperties;
import com.postiva.usage.VerifiedSubscriptionPlanService;
import com.postiva.user.entity.User;
import com.postiva.user.repository.UserRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;

import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@EnabledIfEnvironmentVariable(named = "POSTIVA_TEST_DB_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1",
        "spring.config.import=", "postiva.mail.enabled=false",
        "postiva.openai.api-key=isolated-test-no-provider-calls",
        "app.jwt.secret=only-for-admin-plan-isolated-tests-signing-key",
        "postiva.admin.bootstrap.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AdminPlanIntegrationTest {
    private static final String SCHEMA = "admin_plan_" + UUID.randomUUID().toString().replace("-", "");
    private static final String URL = System.getenv("POSTIVA_TEST_DB_URL");
    private static final String DB_USER = System.getenv().getOrDefault("POSTIVA_TEST_DB_USERNAME", "postgres");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("POSTIVA_TEST_DB_PASSWORD", "");
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired AdminPlanService plans;
    @Autowired AiUsageService usage;
    @Autowired UsageProperties limits;
    @Autowired VerifiedSubscriptionPlanService subscriptions;
    @MockBean CurrentUserService current;
    @MockBean EmailService email;
    @MockBean OpenAiService ai;
    @MockBean PublishingScheduler scheduler;
    private UUID admin;

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

    @BeforeEach void signInAsAdmin() {
        admin = account(User.Role.ADMIN).getId();
        when(current.requireCurrentUserId()).thenReturn(admin);
    }

    @Test void grantedProWorksLikePaidProAndRenewalsChain() {
        User user = account(User.Role.USER);
        assertEquals("FREE", usage.summary(user.getId()).plan());

        Instant before = Instant.now();
        AdminPlanService.Plan first = plans.grantPro(user.getId(), 30, "Khách chuyển khoản trực tiếp");
        assertEquals("PRO", first.currentPlan());
        assertTrue(Duration.between(before.plus(Duration.ofDays(30)), first.proUntil()).abs().toMinutes() < 1);
        assertEquals("PRO", usage.summary(user.getId()).plan());
        assertEquals(limits.getProAiLimit(), usage.summary(user.getId()).limit());

        AdminPlanService.Plan renewed = plans.grantPro(user.getId(), 90, "Gia hạn quý");
        assertEquals(first.proUntil().plus(Duration.ofDays(90)), renewed.proUntil());
        assertEquals(2, renewed.history().size());
        assertTrue(renewed.hasAdminGrantedPro());
    }

    @Test void revokeTakesBackOnlyAdminGrantsAndIsAudited() {
        User user = account(User.Role.USER);
        Instant now = Instant.now();
        subscriptions.activateProFromVerifiedPayment(user.getId(), now, now.plus(Duration.ofDays(10)), "PAYOS:" + UUID.randomUUID());
        AdminPlanService.Plan granted = plans.grantPro(user.getId(), 30, "Tặng thêm");
        assertTrue(Duration.between(now.plus(Duration.ofDays(40)), granted.proUntil()).abs().toMinutes() < 1);

        AdminPlanService.Plan revoked = plans.revokePro(user.getId(), "Cấp nhầm");
        // The paid ten days stay; the gift is gone.
        assertEquals("PRO", revoked.currentPlan());
        assertTrue(Duration.between(now.plus(Duration.ofDays(10)), revoked.proUntil()).abs().toMinutes() < 1);
        assertFalse(revoked.hasAdminGrantedPro());
        assertEquals("REVOKE_PRO", revoked.history().getFirst().action());
        assertTrue(revoked.history().get(1).revoked());

        var nothingLeft = assertThrows(ResponseStatusException.class, () -> plans.revokePro(user.getId(), "Lần nữa"));
        assertEquals(HttpStatus.CONFLICT, nothingLeft.getStatusCode());
        assertThrows(Exception.class, () -> jdbc.update("UPDATE admin_plan_grants SET reason = 'changed'"));
        assertThrows(Exception.class, () -> jdbc.update("DELETE FROM admin_plan_grants"));
    }

    @Test void revokingTheOnlyProReturnsTheUserToFree() {
        User user = account(User.Role.USER);
        plans.grantPro(user.getId(), 30, "Dùng thử Pro");
        plans.revokePro(user.getId(), "Hết thời gian dùng thử");
        assertEquals("FREE", usage.summary(user.getId()).plan());
        assertNull(plans.plan(user.getId()).proUntil());
    }

    @Test void refusesLockedAccountsAdminsAndBadInput() {
        User locked = account(User.Role.USER);
        jdbc.update("UPDATE users SET status = 'LOCKED', lock_reason = 'fixture', locked_at = now(), locked_by = ? WHERE id = ?", admin, locked.getId());
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> plans.grantPro(locked.getId(), 30, "Không được")).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> plans.grantPro(account(User.Role.ADMIN).getId(), 30, "Admin")).getStatusCode());
        User user = account(User.Role.USER);
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> plans.grantPro(user.getId(), 0, "Sai số ngày")).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> plans.grantPro(user.getId(), 30, "  ")).getStatusCode());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM admin_plan_grants WHERE target_user_id = ?", Integer.class, user.getId()));
    }

    private User account(User.Role role) {
        User user = new User();
        user.setEmail(UUID.randomUUID() + "@example.test");
        user.setFullName("Plan fixture");
        user.setRole(role);
        user.setPasswordHash("unused-hash");
        return users.saveAndFlush(user);
    }
}
