package com.postiva.billing;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SePay watches the shop's bank account. A transfer with the right amount and the content
 * POSTIVA&lt;order code&gt; pays that order at once, so the buyer's QR code disappears and Pro turns on
 * without anyone pressing a button. Postiva asks SePay itself (API token) while a buyer waits,
 * and SePay may also push transfers to the webhook (API key) when the backend has a public address.
 */
@Service
public class SePayService {
    private static final Logger log = LoggerFactory.getLogger(SePayService.class);
    // Banks may add spaces, change case or append their own text around the content the buyer typed.
    private static final Pattern ORDER_CODE = Pattern.compile("POSTIVA\\s*(\\d{5,12})", Pattern.CASE_INSENSITIVE);
    // SePay allows a couple of calls a second; a waiting buyer's page asks every few seconds.
    private static final long MIN_CHECK_GAP_MS = 5_000;
    private final BillingProperties properties;
    private final BillingStore store;
    private final BillingService billing;
    private final SePayClient client;
    private long lastCheckAt;

    public SePayService(BillingProperties properties, BillingStore store, BillingService billing, SePayClient client) {
        this.properties = properties; this.store = store; this.billing = billing; this.client = client;
    }

    public record Transfer(String id, String accountNumber, long amount, String content, String reference) { }

    /** Webhook: SePay pushes one transfer, authenticated with the shared API key. */
    public String handle(String authorization, JsonNode body) {
        String key = properties.getSepayApiKey();
        if (key.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Chưa cấu hình SePay");
        String expected = "Apikey " + key;
        if (authorization == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                authorization.trim().getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sai khóa SePay");
        }
        if (body == null || !"in".equalsIgnoreCase(body.path("transferType").asText(""))) return "IGNORED";
        return apply(new Transfer(body.path("id").asText(""), body.path("accountNumber").asText(""),
                body.path("transferAmount").asLong(-1),
                body.path("content").asText("") + " " + body.path("description").asText(""),
                body.path("referenceCode").asText("")));
    }

    /** A buyer is looking at an unpaid transfer: ask SePay now (at most every few seconds). */
    public void checkFor(BillingStore.Order order) {
        if (BillingStore.BANK_TRANSFER.equals(order.provider()) && List.of("PENDING", "EXPIRED").contains(order.status())) checkNow();
    }

    /** Backstop for buyers who closed the page right after paying. */
    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void checkInBackground() { checkNow(); }

    public synchronized void checkNow() {
        if (properties.getSepayApiToken().isBlank() || !properties.bankTransferReady()) return;
        long now = System.currentTimeMillis();
        if (now - lastCheckAt < MIN_CHECK_GAP_MS) return;
        lastCheckAt = now;
        Instant oldest = store.oldestOpenBankTransfer();
        if (oldest == null) return;
        try {
            // A day before the oldest order: SePay does not say which time zone its date filter uses,
            // and re-reading a few old transfers is harmless (each one is handled once).
            for (Transfer transfer : client.incomingSince(oldest.minus(Duration.ofDays(1)))) apply(transfer);
        } catch (Exception exception) {
            log.warn("SePay check failed ({}); will retry", exception.getClass().getSimpleName());
        }
    }

    String apply(Transfer transfer) {
        String account = transfer.accountNumber() == null ? "" : transfer.accountNumber().replace(" ", "");
        if (!account.isEmpty() && !account.equals(properties.getBankAccountNo())) return "OTHER_ACCOUNT";
        Matcher match = ORDER_CODE.matcher(transfer.content() == null ? "" : transfer.content());
        if (!match.find()) return "NO_ORDER_CODE";
        long orderCode = Long.parseLong(match.group(1));
        // The bank's own reference when SePay has it; SePay's transaction id otherwise. Either is unique per transfer.
        String reference = transfer.reference() == null ? "" : transfer.reference().replaceAll("[^A-Za-z0-9._/-]", "");
        if (reference.length() < 4) reference = "SEPAY-" + transfer.id().replaceAll("[^0-9]", "");
        if (reference.length() < 4 || transfer.amount() <= 0) return "INCOMPLETE";
        String eventKey = "SEPAY:" + (transfer.id().isBlank() ? reference : transfer.id());
        String decision = store.acceptBankTransfer(orderCode, transfer.amount(), reference.toUpperCase(Locale.ROOT), eventKey);
        if ("PAID".equals(decision)) billing.grantPaid(orderCode);
        if (!"DUPLICATE".equals(decision)) log.info("SePay transfer for order {}: {}", orderCode, decision);
        return decision;
    }
}
