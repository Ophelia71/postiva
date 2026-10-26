package com.postiva.automation;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Daily auto-posting switches. Everything is off by default: the feature must be enabled
 * explicitly, and unattended publishing (AUTO) needs its own second switch.
 */
@Component
@ConfigurationProperties(prefix = "postiva.automation")
@Validated
public class AutomationProperties {
    private boolean enabled = false;
    private boolean autoEnabled = false;
    @Min(1) @Max(20) private int maxRulesPerUser = 3;
    /** Quota units kept for manual use; a rule pauses instead of spending the last ones. */
    @Min(0) @Max(30) private int manualQuotaReserve = 3;
    @Min(1000) private long plannerDelayMs = 300_000;
    @Min(60) private long leaseSeconds = 900;
    /** After this many generation attempts a day is skipped instead of retried again. */
    @Min(1) @Max(10) private int maxGenerationAttempts = 3;
    @Min(60) private long retryDelaySeconds = 600;
    /** Email the owner when a draft is waiting for review. It only sends when mail itself is configured. */
    private boolean notifyEmail = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isAutoEnabled() { return autoEnabled; }
    public void setAutoEnabled(boolean autoEnabled) { this.autoEnabled = autoEnabled; }
    public int getMaxRulesPerUser() { return maxRulesPerUser; }
    public void setMaxRulesPerUser(int value) { this.maxRulesPerUser = value; }
    public int getManualQuotaReserve() { return manualQuotaReserve; }
    public void setManualQuotaReserve(int value) { this.manualQuotaReserve = value; }
    public long getPlannerDelayMs() { return plannerDelayMs; }
    public void setPlannerDelayMs(long value) { this.plannerDelayMs = value; }
    public long getLeaseSeconds() { return leaseSeconds; }
    public void setLeaseSeconds(long value) { this.leaseSeconds = value; }
    public int getMaxGenerationAttempts() { return maxGenerationAttempts; }
    public void setMaxGenerationAttempts(int value) { this.maxGenerationAttempts = value; }
    public boolean isNotifyEmail() { return notifyEmail; }
    public void setNotifyEmail(boolean value) { this.notifyEmail = value; }
    public long getRetryDelaySeconds() { return retryDelaySeconds; }
    public void setRetryDelaySeconds(long value) { this.retryDelaySeconds = value; }
}
