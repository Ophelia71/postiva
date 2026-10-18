package com.postiva.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Component
@ConfigurationProperties(prefix = "postiva.ai.cost")
@Validated
public class AiCostProperties {
    private boolean enabled = true;
    private String openAiAdminKey = "";
    private String openAiProjectId = "";
    @DecimalMin("0.01") private BigDecimal dailyBudgetUsd = new BigDecimal("10.00");
    @DecimalMin("0.01") private BigDecimal monthlyBudgetUsd = new BigDecimal("100.00");
    @DecimalMin("0.001") private BigDecimal inputUsdPerMillionTokens = new BigDecimal("0.10");
    @DecimalMin("0.001") private BigDecimal cachedInputUsdPerMillionTokens = new BigDecimal("0.01");
    @DecimalMin("0.001") private BigDecimal cacheWriteInputUsdPerMillionTokens = new BigDecimal("0.125");
    @DecimalMin("0.001") private BigDecimal outputUsdPerMillionTokens = new BigDecimal("0.50");
    @DecimalMin("1.0") private BigDecimal reservationMultiplier = new BigDecimal("2.0");
    @Min(32000) private int maxInputTokens = 32_000;
    @Min(1800) private int maxOutputTokens = 1_800;
    @Min(1) @Max(99) private int warningPercent = 80;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public String getOpenAiAdminKey() { return openAiAdminKey; }
    public void setOpenAiAdminKey(String value) { openAiAdminKey = value; }
    public String getOpenAiProjectId() { return openAiProjectId; }
    public void setOpenAiProjectId(String value) { openAiProjectId = value; }
    public boolean isProviderReconciliationEnabled() {
        return openAiAdminKey != null && !openAiAdminKey.isBlank()
                && openAiProjectId != null && !openAiProjectId.isBlank();
    }
    public BigDecimal getDailyBudgetUsd() { return dailyBudgetUsd; }
    public void setDailyBudgetUsd(BigDecimal value) { dailyBudgetUsd = value; }
    public BigDecimal getMonthlyBudgetUsd() { return monthlyBudgetUsd; }
    public void setMonthlyBudgetUsd(BigDecimal value) { monthlyBudgetUsd = value; }
    public BigDecimal getInputUsdPerMillionTokens() { return inputUsdPerMillionTokens; }
    public void setInputUsdPerMillionTokens(BigDecimal value) { inputUsdPerMillionTokens = value; }
    public BigDecimal getCachedInputUsdPerMillionTokens() { return cachedInputUsdPerMillionTokens; }
    public void setCachedInputUsdPerMillionTokens(BigDecimal value) { cachedInputUsdPerMillionTokens = value; }
    public BigDecimal getCacheWriteInputUsdPerMillionTokens() { return cacheWriteInputUsdPerMillionTokens; }
    public void setCacheWriteInputUsdPerMillionTokens(BigDecimal value) { cacheWriteInputUsdPerMillionTokens = value; }
    public BigDecimal getOutputUsdPerMillionTokens() { return outputUsdPerMillionTokens; }
    public void setOutputUsdPerMillionTokens(BigDecimal value) { outputUsdPerMillionTokens = value; }
    public BigDecimal getReservationMultiplier() { return reservationMultiplier; }
    public void setReservationMultiplier(BigDecimal value) { reservationMultiplier = value; }
    public int getMaxInputTokens() { return maxInputTokens; }
    public void setMaxInputTokens(int value) { maxInputTokens = value; }
    public int getMaxOutputTokens() { return maxOutputTokens; }
    public void setMaxOutputTokens(int value) { maxOutputTokens = value; }
    public int getWarningPercent() { return warningPercent; }
    public void setWarningPercent(int value) { warningPercent = value; }
}
