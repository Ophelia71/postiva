package com.postiva.usage;

import com.postiva.ai.dto.ProviderTokenUsage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiCostCalculationTest {
    @Test
    void estimatesStandardCachedCacheWriteAndOutputTokensAtSeparateRates() {
        var usage = new ProviderTokenUsage(1_000, 200, 100, 500, "gpt-6-luna", "request-1");

        BigDecimal result = AiCostService.calculateCost(usage,
                new BigDecimal("0.10"), new BigDecimal("0.01"), new BigDecimal("0.125"), new BigDecimal("0.50"));

        assertEquals(new BigDecimal("0.0003345000"), result);
    }
}
