package com.postiva.billing;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PaymentRulesTest {
    private final Instant now = Instant.parse("2026-10-17T00:00:00Z");

    @Test void amountCurrencyLinkRecipientAndReferenceMustMatchTheOrder() {
        var order = order("PENDING");
        assertNull(PaymentRules.mismatch(order, 99_000, "VND", "link1234567890", "12345678", "TX-123456"));
        assertEquals("AMOUNT_OR_CURRENCY_MISMATCH", PaymentRules.mismatch(order, 98_000, "VND", "link1234567890", "12345678", "TX-123456"));
        assertEquals("AMOUNT_OR_CURRENCY_MISMATCH", PaymentRules.mismatch(order, 99_000, "USD", "link1234567890", "12345678", "TX-123456"));
        assertEquals("LINK_MISMATCH", PaymentRules.mismatch(order, 99_000, "VND", "other-link-123", "12345678", "TX-123456"));
        assertEquals("RECIPIENT_MISMATCH", PaymentRules.mismatch(order, 99_000, "VND", "link1234567890", "87654321", "TX-123456"));
        assertEquals("TRANSACTION_REFERENCE_MISSING", PaymentRules.mismatch(order, 99_000, "VND", "link1234567890", "12345678", ""));
    }

    @Test void paidNeverDowngradesAndExpiredCanLaterBePaid() {
        assertEquals("PAID", PaymentRules.nextUnpaidStatus("PAID", "CANCELLED", now, now.plusSeconds(1)));
        assertEquals("REVIEW", PaymentRules.nextUnpaidStatus("REVIEW", "PENDING", now, now.plusSeconds(1)));
        assertEquals("EXPIRED", PaymentRules.nextUnpaidStatus("PENDING", "PENDING", now, now.plusSeconds(1)));
        assertEquals("CANCELLED", PaymentRules.nextUnpaidStatus("PENDING", "CANCELLED", now.plusSeconds(900), now));
        assertEquals("PENDING", PaymentRules.nextUnpaidStatus("CREATING", "PENDING", now.plusSeconds(900), now));
        assertNull(PaymentRules.mismatch(order("EXPIRED"), 99_000, "VND", "link1234567890", "12345678", "TX-123456"));
    }

    private BillingStore.Order order(String status) {
        return new BillingStore.Order(UUID.randomUUID(), UUID.randomUUID(), 1234567, 99_000, 50, 30, status,
                "NOT_APPLICABLE", "link1234567890", null, "12345678", null, now.plusSeconds(900), null, now, null, null);
    }
}
