package com.postiva.billing;

import com.postiva.ai.service.OpenAiService;
import com.postiva.mail.EmailService;
import com.postiva.scheduler.PublishingScheduler;
import com.postiva.usage.AiUsageService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.sql.DriverManager;
import java.sql.Timestamp;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Real PostgreSQL checks are isolated from the app database and enabled only by an explicit test URL. */
@EnabledIfEnvironmentVariable(named = "POSTIVA_TEST_DB_URL", matches = ".+")
@SpringBootTest(properties = {"spring.config.import=", "postiva.mail.enabled=false",
        "postiva.openai.api-key=isolated-test-key", "postiva.admin.bootstrap.enabled=false",
        "postiva.billing.enabled=true", "postiva.billing.pro-price-vnd=99000",
        "postiva.billing.client-id=isolated-test-client", "postiva.billing.api-key=isolated-test-api-key",
        "postiva.billing.checksum-key=isolated-test-checksum-key-with-32-bytes",
        "postiva.billing.return-url=https://example.test/app/billing",
        "postiva.billing.cancel-url=https://example.test/app/billing",
        "postiva.billing.refund-policy-url=https://example.test/refunds",
        "app.jwt.secret=only-for-billing-isolated-tests-signing-key"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BillingIntegrationTest {
    private static final String SCHEMA = "billing_test_" + UUID.randomUUID().toString().replace("-", "");
    private static final String URL = System.getenv("POSTIVA_TEST_DB_URL");
    private static final String DB_USER = System.getenv().getOrDefault("POSTIVA_TEST_DB_USERNAME", "postgres");
    private static final String DB_PASSWORD = System.getenv().getOrDefault("POSTIVA_TEST_DB_PASSWORD", "");
    @Autowired JdbcTemplate jdbc;
    @Autowired BillingStore store;
    @Autowired BillingGrantService grants;
    @Autowired RefundService refunds;
    @Autowired AiUsageService usage;
    @MockBean OpenAiService ai;
    @MockBean EmailService email;
    @MockBean PublishingScheduler scheduler;
    @MockBean PayOsGateway payos;
    @Autowired BillingService billing;
    @Autowired BillingProperties properties;

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        if (URL == null || !URL.matches("jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]+/postiva_migration_test(\\?.*)?"))
            throw new IllegalStateException("Dedicated local postiva_migration_test database required");
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

    @Test void duplicateAndLateCancellationCannotDuplicateOrRevokePaidEntitlement() {
        UUID user = user();
        assertEquals("FREE", usage.summary(user).plan());
        BillingStore.Order order = draft(user);
        var paid = payment(order, "BANK-REF-ONE", "a".repeat(64), order.amountVnd());
        assertEquals("PAID", store.accept(paid).decision());
        grants.grant(order.id());
        assertEquals("DUPLICATE", store.accept(paid).decision());
        store.applyProviderStatus(new PayOsGateway.Status(order.orderCode(), order.amountVnd(), 0,
                "CANCELLED", order.linkId(), "12345678", ""));
        BillingStore.Order after = store.require(order.id(), user);
        assertEquals("PAID", after.status());
        assertEquals("GRANTED", after.grantStatus());
        assertEquals("PRO", usage.summary(user).plan());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM subscription_periods WHERE source_reference = ?",
                Integer.class, "PAYOS:" + order.orderCode()));
    }

    @Test void wrongAmountGoesToReviewButValidLatePaymentCanStillGrant() {
        UUID user = user(); BillingStore.Order order = draft(user);
        assertEquals("REVIEW", store.accept(payment(order, "BANK-REF-TWO", "b".repeat(64), order.amountVnd() - 1)).decision());
        grants.grant(order.id());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM subscription_periods WHERE source_reference = ?",
                Integer.class, "PAYOS:" + order.orderCode()));
        assertEquals("PAID", store.accept(payment(order, "BANK-REF-TWO", "c".repeat(64), order.amountVnd())).decision());
        grants.grant(order.id());
        assertEquals("GRANTED", store.require(order.id(), user).grantStatus());
    }

    @Test void reconciliationBackoffKeepsNewOrdersEligibleWithoutHammeringProvider() {
        UUID user = user();
        BillingStore.Order order = draft(user);
        assertTrue(store.reconcileCandidates().stream().anyMatch(candidate -> candidate.id().equals(order.id())));
        store.noteReconcileAttempt(order.id());
        assertTrue(store.reconcileCandidates().stream().noneMatch(candidate -> candidate.id().equals(order.id())));
    }

    @Test void renewalQueuesNextPeriodAndFullExternalRefundVoidsOnlyItsOwnPeriod() {
        UUID user = user(); UUID admin = admin();
        BillingStore.Order first = draft(user);
        store.accept(payment(first, "BANK-REF-THREE", "d".repeat(64), first.amountVnd()));
        grants.grant(first.id());
        BillingStore.Order second = draft(user);
        store.accept(payment(second, "BANK-REF-FOUR", "e".repeat(64), second.amountVnd()));
        grants.grant(second.id());
        Timestamp firstEnd = jdbc.queryForObject("SELECT ends_at FROM subscription_periods WHERE id = ?", Timestamp.class,
                store.require(first.id(), user).periodId());
        Timestamp secondStart = jdbc.queryForObject("SELECT starts_at FROM subscription_periods WHERE id = ?", Timestamp.class,
                store.require(second.id(), user).periodId());
        assertEquals(firstEnd.toInstant(), secondStart.toInstant());
        var refund = refunds.request(user, first.id(), "Khách hàng yêu cầu hoàn toàn bộ kỳ đầu");
        refunds.decide(admin, refund.id(), true, "Đã kiểm tra chính sách hoàn tiền");
        refunds.startExternalRefund(admin, refund.id(), "Đang hoàn bên ngoài Postiva");
        refunds.settle(admin, refund.id(), "BANK-REFUND-123", "Đã đối chiếu chứng từ ngân hàng");
        assertEquals("true", String.valueOf(jdbc.queryForObject("SELECT voided_at IS NOT NULL FROM subscription_periods WHERE id = ?",
                Boolean.class, store.require(first.id(), user).periodId())));
        assertEquals("false", String.valueOf(jdbc.queryForObject("SELECT voided_at IS NOT NULL FROM subscription_periods WHERE id = ?",
                Boolean.class, store.require(second.id(), user).periodId())));
        assertEquals("NOT_APPLICABLE", store.require(first.id(), user).grantStatus());
        assertEquals("GRANTED", store.require(second.id(), user).grantStatus());
        assertEquals("FREE", usage.summary(user).plan());
    }

    @Test void buyingAgainAfterCancellingOnThePayOsPageStartsAFreshOrder() {
        openSales();
        UUID user = user();
        BillingStore.Order first = draft(user);
        org.mockito.Mockito.when(payos.lookup(first.orderCode())).thenReturn(status(first, "CANCELLED", 0, ""));
        org.mockito.Mockito.when(payos.create(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong())).thenAnswer(call -> {
            long code = call.getArgument(0);
            return new PayOsGateway.Link(code, call.getArgument(1), "link" + code, "https://pay.payos.vn/web/link" + code, "12345678");
        });
        BillingStore.Order second = billing.create(user, UUID.randomUUID());
        assertNotEquals(first.id(), second.id());
        assertEquals("PENDING", second.status());
        assertEquals("CANCELLED", store.require(first.id(), user).status());
    }

    @Test void anOrderStillWaitingOnPayOsIsReusedNotDuplicated() {
        openSales();
        UUID user = user();
        BillingStore.Order first = draft(user);
        org.mockito.Mockito.when(payos.lookup(first.orderCode())).thenReturn(status(first, "PENDING", 0, ""));
        assertEquals(first.id(), billing.create(user, UUID.randomUUID()).id());
        org.mockito.Mockito.verify(payos, org.mockito.Mockito.never()).create(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test void cancellingALinkPayOsAlreadyClosedOnlyRecordsIt() {
        UUID user = user();
        BillingStore.Order order = draft(user);
        org.mockito.Mockito.when(payos.lookup(order.orderCode())).thenReturn(status(order, "CANCELLED", 0, ""));
        assertEquals("CANCELLED", billing.cancel(user, order.id()).status());
        org.mockito.Mockito.verify(payos, org.mockito.Mockito.never()).cancel(order.orderCode());
    }

    @Test void checkingOnReturnTurnsProOnWithoutWaitingForTheWebhook() {
        UUID user = user();
        BillingStore.Order order = draft(user);
        org.mockito.Mockito.when(payos.lookup(order.orderCode())).thenReturn(status(order, "PAID", order.amountVnd(), "FT-RETURN-1"));
        BillingStore.Order after = billing.refresh(user, order.id());
        assertEquals("PAID", after.status());
        assertEquals("GRANTED", after.grantStatus());
        assertEquals("PRO", usage.summary(user).plan());
    }

    private void openSales() {
        properties.setEnabled(true);
        properties.setProPriceVnd(2_000);
        properties.setClientId("client"); properties.setApiKey("api");
        properties.setChecksumKey("12345678901234567890123456789012");
        properties.setReturnUrl("http://localhost:5173/app/billing");
        properties.setCancelUrl("http://localhost:5173/app/billing");
        properties.setRefundPolicyUrl("http://localhost:5173/refund-policy");
    }

    private PayOsGateway.Status status(BillingStore.Order order, String state, long paid, String reference) {
        return new PayOsGateway.Status(order.orderCode(), order.amountVnd(), paid, state, order.linkId(), "12345678", reference);
    }

    private BillingStore.Order draft(UUID user) {
        var order = store.draft(user, UUID.randomUUID());
        String linkId = "link" + order.orderCode();
        store.recordLink(order.id(), new PayOsGateway.Link(order.orderCode(), order.amountVnd(),
                linkId, "https://pay.payos.vn/web/" + linkId, "12345678"));
        return store.require(order.id(), user);
    }

    private PayOsGateway.VerifiedPayment payment(BillingStore.Order order, String ref, String signature, long amount) {
        return new PayOsGateway.VerifiedPayment(order.orderCode(), amount, "VND", order.linkId(),
                "12345678", ref, true, signature);
    }

    private UUID user() { return createUser("USER"); }
    private UUID admin() { return createUser("ADMIN"); }
    private UUID createUser(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, full_name, email, password_hash, role) VALUES (?, ?, ?, ?, ?)",
                id, "Billing fixture", id + "@example.test", "fixture-hash", role);
        return id;
    }
}
