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
    @Min(0) private int proAiLimit = 50;
    @Min(1) @Max(366) private int periodDays = 30;
    @Min(0) private int globalDailyAttempts = 100;
    @Min(1) private int userDailyAttempts = 20;
    public int getFreeAiLimit() { return freeAiLimit; }
    public void setFreeAiLimit(int value) { freeAiLimit = value; }
    public int getProAiLimit() { return proAiLimit; }
    public void setProAiLimit(int value) { proAiLimit = value; }
    public int getPeriodDays() { return periodDays; }
    public void setPeriodDays(int value) { periodDays = value; }
    public int getGlobalDailyAttempts() { return globalDailyAttempts; }
    public void setGlobalDailyAttempts(int value) { globalDailyAttempts = value; }
    public int getUserDailyAttempts() { return userDailyAttempts; }
    public void setUserDailyAttempts(int value) { userDailyAttempts = value; }
}
