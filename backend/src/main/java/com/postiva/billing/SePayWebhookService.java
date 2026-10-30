package com.postiva.billing;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SePay watches the shop's bank account and posts every incoming transfer here.
 * A transfer with the right amount and the content POSTIVA&lt;order code&gt; pays that order at once,
 * so the buyer's QR code disappears and Pro turns on without anyone pressing a button.
 */
@Service
public class SePayWebhookService {
    private static final Logger log = LoggerFactory.getLogger(SePayWebhookService.class);
    // Banks may add spaces, change case or append their own text around the content the buyer typed.
    private static final Pattern ORDER_CODE = Pattern.compile("POSTIVA\\s*(\\d{5,12})", Pattern.CASE_INSENSITIVE);
    private final BillingProperties properties;
    private final BillingStore store;
    private final BillingService billing;

    public SePayWebhookService(BillingProperties properties, BillingStore store, BillingService billing) {
        this.properties = properties; this.store = store; this.billing = billing;
    }

    public String handle(String authorization, JsonNode body) {
        String key = properties.getSepayApiKey();
        if (key.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Chưa cấu hình SePay");
        String expected = "Apikey " + key;
        if (authorization == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                authorization.trim().getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sai khóa SePay");
        }
        if (body == null || !"in".equalsIgnoreCase(body.path("transferType").asText(""))) return "IGNORED";
        String account = body.path("accountNumber").asText("").replace(" ", "");
        if (!account.isEmpty() && !account.equals(properties.getBankAccountNo())) return "OTHER_ACCOUNT";
        Matcher match = ORDER_CODE.matcher(body.path("content").asText("") + " " + body.path("description").asText(""));
        if (!match.find()) return "NO_ORDER_CODE";
        long orderCode = Long.parseLong(match.group(1));
        long amount = body.path("transferAmount").asLong(-1);
        // The bank's own reference when SePay has it; SePay's transaction id otherwise. Either is unique per transfer.
        String reference = body.path("referenceCode").asText("").replaceAll("[^A-Za-z0-9._/-]", "");
        if (reference.length() < 4) reference = "SEPAY-" + body.path("id").asText("").replaceAll("[^0-9]", "");
        if (reference.length() < 4 || amount <= 0) return "INCOMPLETE";
        String decision = store.acceptBankTransfer(orderCode, amount, reference.toUpperCase(Locale.ROOT), "SEPAY:" + body.path("id").asText(reference));
        if ("PAID".equals(decision)) billing.grantPaid(orderCode);
        log.info("SePay transfer for order {}: {}", orderCode, decision);
        return decision;
    }
}
