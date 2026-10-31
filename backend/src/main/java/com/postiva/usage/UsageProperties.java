package com.postiva.usage;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "postiva.usage")
@Validated
public class UsageProperties {
    @Min(0) private int freeAiLimit = 30;
    @Min(0) private int proAiLimit = 300;
    @Min(1) @Max(366) private int periodDays = 30;
    @Min(0) private int globalDailyAttempts = 100;
    /** AI requests one person may make per UTC day (07:00 in Vietnam), by plan. Failed calls do not count. */
    @Min(1) private int freeDailyAttempts = 10;
    @Min(1) private int proDailyAttempts = 60;
    public int getFreeAiLimit() { return freeAiLimit; }
    public void setFreeAiLimit(int value) { freeAiLimit = value; }
    public int getProAiLimit() { return proAiLimit; }
    public void setProAiLimit(int value) { proAiLimit = value; }
    public int getPeriodDays() { return periodDays; }
    public void setPeriodDays(int value) { periodDays = value; }
    public int getGlobalDailyAttempts() { return globalDailyAttempts; }
    public void setGlobalDailyAttempts(int value) { globalDailyAttempts = value; }
    public int getFreeDailyAttempts() { return freeDailyAttempts; }
    public void setFreeDailyAttempts(int value) { freeDailyAttempts = value; }
    public int getProDailyAttempts() { return proDailyAttempts; }
    public void setProDailyAttempts(int value) { proDailyAttempts = value; }

    public int dailyAttemptsFor(String planCode) {
        return "PRO".equals(planCode) ? proDailyAttempts : freeDailyAttempts;
    }
}
