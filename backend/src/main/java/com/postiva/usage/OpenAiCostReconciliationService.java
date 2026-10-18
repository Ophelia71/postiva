package com.postiva.usage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.postiva.config.AiCostProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

/** Imports OpenAI's project-filtered organization cost buckets when an Admin API key is configured. */
@Service
public class OpenAiCostReconciliationService {
    private static final Logger log = LoggerFactory.getLogger(OpenAiCostReconciliationService.class);
    private final AiCostProperties properties;
    private final AiCostService costs;
    private final ObjectMapper json;
    private final WebClient webClient;

    public OpenAiCostReconciliationService(AiCostProperties properties, AiCostService costs,
                                           ObjectMapper json, WebClient.Builder builder) {
        this.properties = properties;
        this.costs = costs;
        this.json = json;
        this.webClient = builder.clone().baseUrl("https://api.openai.com/v1").build();
    }

    @Scheduled(fixedDelay = 21_600_000, initialDelay = 120_000)
    public void reconcileRecentCosts() {
        if (!properties.isProviderReconciliationEnabled()) return;
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant start = today.minusDays(180).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = Instant.now();
        try {
            Map<LocalDate, BigDecimal> daily = fetch(start, end);
            if (!daily.isEmpty()) costs.reconcileProviderCosts(daily, today);
        } catch (Exception exception) {
            log.warn("OpenAI organization cost reconciliation failed (HTTP/API unavailable); token-based estimates remain active: {}",
                    exception.getClass().getSimpleName());
        }
    }

    Map<LocalDate, BigDecimal> fetch(Instant start, Instant end) throws Exception {
        Map<LocalDate, BigDecimal> daily = new LinkedHashMap<>();
        String nextPage = null;
        for (int page = 0; page < 10; page++) {
            final String cursor = nextPage;
            String body = webClient.get().uri(builder -> {
                        var uri = builder.path("/organization/costs")
                                .queryParam("start_time", start.getEpochSecond())
                                .queryParam("end_time", end.getEpochSecond())
                                .queryParam("bucket_width", "1d")
                                .queryParam("limit", 180)
                                .queryParam("project_ids", properties.getOpenAiProjectId());
                        if (cursor != null && !cursor.isBlank()) uri.queryParam("page", cursor);
                        return uri.build();
                    })
                    .header("Authorization", "Bearer " + properties.getOpenAiAdminKey())
                    .retrieve().bodyToMono(String.class).block(java.time.Duration.ofSeconds(20));
            if (body == null || body.isBlank()) throw new IllegalStateException("Empty cost response");
            JsonNode root = json.readTree(body);
            JsonNode buckets = root.path("data");
            if (!buckets.isArray()) throw new IllegalStateException("Invalid cost response");
            parseBuckets(body).forEach((date, amount) -> daily.merge(date, amount, BigDecimal::add));
            nextPage = root.path("next_page").asText(null);
            if (nextPage == null || nextPage.isBlank() || !root.path("has_more").asBoolean(false)) return daily;
        }
        throw new IllegalStateException("Cost response exceeded page safety limit");
    }

    Map<LocalDate, BigDecimal> parseBuckets(String body) throws Exception {
        Map<LocalDate, BigDecimal> daily = new LinkedHashMap<>();
        JsonNode buckets = json.readTree(body).path("data");
        if (!buckets.isArray()) throw new IllegalStateException("Invalid cost response");
        for (JsonNode bucket : buckets) {
            if (!bucket.path("start_time").canConvertToLong()) continue;
            LocalDate date = Instant.ofEpochSecond(bucket.path("start_time").asLong()).atZone(ZoneOffset.UTC).toLocalDate();
            BigDecimal amount = BigDecimal.ZERO;
            JsonNode results = bucket.path("results");
            if (results.isArray()) for (JsonNode result : results) {
                String currency = result.path("amount").path("currency").asText("");
                if (!"usd".equalsIgnoreCase(currency) || !result.path("amount").path("value").isNumber()) {
                    throw new IllegalStateException("Unsupported currency or amount in cost response");
                }
                amount = amount.add(result.path("amount").path("value").decimalValue());
            }
            daily.merge(date, amount, BigDecimal::add);
        }
        return daily;
    }
}
