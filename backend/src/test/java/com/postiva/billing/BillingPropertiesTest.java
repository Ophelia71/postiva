package com.postiva.billing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BillingPropertiesTest {
    @Test void pausingSalesStillAllowsWebhookAndReconciliationForExistingOrders() {
        BillingProperties settings = new BillingProperties();
        settings.setClientId("client"); settings.setApiKey("api");
        settings.setChecksumKey("12345678901234567890123456789012");
        settings.setReturnUrl("https://example.test/app/billing");
        settings.setCancelUrl("https://example.test/app/billing");
        settings.setRefundPolicyUrl("https://example.test/refunds");
        settings.setProPriceVnd(99_000);
        assertTrue(settings.providerConfigured());
        assertFalse(settings.ready());
        settings.setEnabled(true);
        assertTrue(settings.ready());
    }
}
