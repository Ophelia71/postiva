package com.postiva.billing;

import java.time.Instant;

final class PaymentRules {
    private PaymentRules() { }

    static String mismatch(BillingStore.Order order, long amount, String currency, String linkId,
                           String recipient, String reference) {
        if (amount != order.amountVnd() || !"VND".equals(currency)) return "AMOUNT_OR_CURRENCY_MISMATCH";
        if (linkId == null || !linkId.matches("[A-Za-z0-9_-]{10,100}")
                || (order.linkId() != null && !order.linkId().equals(linkId))) return "LINK_MISMATCH";
        if (reference == null || reference.isBlank() || reference.length() > 160
                || reference.chars().anyMatch(Character::isISOControl)) return "TRANSACTION_REFERENCE_MISSING";
        if (order.recipientAccountNumber() != null && recipient != null && !recipient.isBlank()
                && !order.recipientAccountNumber().equals(recipient)) return "RECIPIENT_MISMATCH";
        return null;
    }

    static String nextUnpaidStatus(String current, String provider, Instant expiresAt, Instant now) {
        if ("PAID".equals(current) || "REVIEW".equals(current)) return current;
        if ("CANCELLED".equals(provider) || "EXPIRED".equals(provider)) return provider;
        if ("PENDING".equals(provider) && !expiresAt.isAfter(now)) return "EXPIRED";
        if ("PENDING".equals(provider) && "CREATING".equals(current)) return "PENDING";
        return current;
    }
}
