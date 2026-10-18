package com.postiva.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PayOsSignatureTest {
    private final ObjectMapper json = new ObjectMapper();
    private PayOsSignature signer;

    @BeforeEach void setUp() {
        BillingProperties properties = new BillingProperties();
        properties.setChecksumKey("1a54716c8f0efb2744fb28b6e38b25da7f67a925d98bc1c18bd8faaecadd7675");
        signer = new PayOsSignature(json, properties);
    }

    @Test void verifiesOfficialWebhookVectorAndRejectsMutations() throws Exception {
        var data = json.readTree("""
                {"orderCode":123,"amount":3000,"description":"VQRIO123","accountNumber":"12345678",
                "reference":"TF230204212323","transactionDateTime":"2023-02-04 18:25:00","currency":"VND",
                "paymentLinkId":"124c33293c43417ab7879e14c8d9eb18","code":"00","desc":"Thành công",
                "counterAccountBankId":"","counterAccountBankName":"","counterAccountName":"",
                "counterAccountNumber":"","virtualAccountName":"","virtualAccountNumber":""}
                """);
        String official = "412e915d2871504ed31be63c8f62a149a4410d34c4c42affc9006ef9917eaa03";
        assertTrue(signer.verify(data, official));
        ((com.fasterxml.jackson.databind.node.ObjectNode) data).put("amount", 3001);
        assertFalse(signer.verify(data, official));
        assertFalse(signer.verify(data, "malformed"));
    }

    @Test void creationSignatureUsesOnlyTheFiveDocumentedFields() {
        assertEquals("amount=99000&cancelUrl=https://example.com/cancel&description=1234567&orderCode=1234567&returnUrl=https://example.com/return",
                signer.canonical(json.valueToTree(java.util.Map.of("amount", 99000, "cancelUrl", "https://example.com/cancel",
                        "description", "1234567", "orderCode", 1234567, "returnUrl", "https://example.com/return"))));
        assertEquals(64, signer.signCreate(1234567, 99000, "1234567", "https://example.com/return", "https://example.com/cancel").length());
    }
}
