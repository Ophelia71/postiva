package com.postiva.usage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.postiva.config.AiCostProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class OpenAiCostReconciliationServiceTest {
    private final AiCostProperties properties = new AiCostProperties();
    private final OpenAiCostReconciliationService service = new OpenAiCostReconciliationService(
            properties, mock(AiCostService.class), new ObjectMapper(), WebClient.builder());

    @Test
    void groupsProjectCostLineItemsByUtcDay() throws Exception {
        Map<LocalDate, BigDecimal> costs = service.parseBuckets("""
                {"data":[
                  {"start_time":1792195200,"results":[
                    {"amount":{"value":0.12,"currency":"usd"}},
                    {"amount":{"value":0.08,"currency":"USD"}}]},
                  {"start_time":1792281600,"results":[
                    {"amount":{"value":0.25,"currency":"usd"}}]}
                ]}
                """);

        assertEquals(new BigDecimal("0.20"), costs.get(LocalDate.of(2026, 10, 17)));
        assertEquals(new BigDecimal("0.25"), costs.get(LocalDate.of(2026, 10, 18)));
    }

    @Test
    void rejectsNonUsdBillingAmountsInsteadOfUnderReportingSpend() {
        assertThrows(IllegalStateException.class, () -> service.parseBuckets("""
                {"data":[{"start_time":1792195200,"results":[
                  {"amount":{"value":1,"currency":"eur"}}]}]}
                """));
    }
}
