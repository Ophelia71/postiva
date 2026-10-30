package com.postiva.billing;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the shop's recent bank transactions from SePay's user API with the account's API token.
 * Never log the token or the response: it holds the whole account history.
 */
@Component
public class SePayClient {
    // SePay reports and filters times in Vietnam's local time.
    private static final DateTimeFormatter SEPAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
    private final BillingProperties properties;
    private final WebClient client;

    public SePayClient(BillingProperties properties, WebClient.Builder builder) {
        this.properties = properties;
        this.client = builder.clone().baseUrl("https://my.sepay.vn").build();
    }

    /** Money that came into the shop's account since the given moment, newest first. */
    public List<SePayService.Transfer> incomingSince(Instant since) {
        JsonNode body = client.get()
                .uri(uri -> uri.path("/userapi/transactions/list")
                        .queryParam("account_number", properties.getBankAccountNo())
                        .queryParam("transaction_date_min", SEPAY_TIME.format(since))
                        .queryParam("limit", 200)
                        .build())
                .header("Authorization", "Bearer " + properties.getSepayApiToken())
                .retrieve().bodyToMono(JsonNode.class).block(Duration.ofSeconds(10));
        List<SePayService.Transfer> transfers = new ArrayList<>();
        if (body == null || !body.path("transactions").isArray()) return transfers;
        for (JsonNode item : body.path("transactions")) {
            long amountIn = wholeDong(item.path("amount_in").asText("0"));
            if (amountIn <= 0) continue;
            transfers.add(new SePayService.Transfer(item.path("id").asText(""), item.path("account_number").asText(""),
                    amountIn, item.path("transaction_content").asText(""), item.path("reference_number").asText("")));
        }
        return transfers;
    }

    private static long wholeDong(String amount) {
        try {
            BigDecimal value = new BigDecimal(amount.trim());
            return value.stripTrailingZeros().scale() > 0 ? -1 : value.longValueExact();
        } catch (Exception exception) { return -1; }
    }
}
