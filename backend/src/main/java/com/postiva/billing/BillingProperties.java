package com.postiva.billing;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
@ConfigurationProperties(prefix = "postiva.billing")
public class BillingProperties {
    private boolean enabled;
    private long proPriceVnd;
    private String clientId = "";
    private String apiKey = "";
    private String checksumKey = "";
    private String returnUrl = "";
    private String cancelUrl = "";
    private String refundPolicyUrl = "";

    public boolean ready() {
        return enabled && proPriceVnd >= 2_000 && proPriceVnd <= 100_000_000
                && providerConfigured() && https(refundPolicyUrl);
    }

    /** Existing paid orders must still be serviced when sales are paused or the published price changes. */
    public boolean providerConfigured() {
        return !clientId.isBlank() && !apiKey.isBlank() && checksumKey.length() >= 32
                && https(returnUrl) && https(cancelUrl);
    }

    private boolean https(String value) {
        try { URI uri = URI.create(value); return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null; }
        catch (Exception ignored) { return false; }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getProPriceVnd() { return proPriceVnd; }
    public void setProPriceVnd(long proPriceVnd) { this.proPriceVnd = proPriceVnd; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId == null ? "" : clientId; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey == null ? "" : apiKey; }
    public String getChecksumKey() { return checksumKey; }
    public void setChecksumKey(String checksumKey) { this.checksumKey = checksumKey == null ? "" : checksumKey; }
    public String getReturnUrl() { return returnUrl; }
    public void setReturnUrl(String returnUrl) { this.returnUrl = returnUrl == null ? "" : returnUrl; }
    public String getCancelUrl() { return cancelUrl; }
    public void setCancelUrl(String cancelUrl) { this.cancelUrl = cancelUrl == null ? "" : cancelUrl; }
    public String getRefundPolicyUrl() { return refundPolicyUrl; }
    public void setRefundPolicyUrl(String refundPolicyUrl) { this.refundPolicyUrl = refundPolicyUrl == null ? "" : refundPolicyUrl; }
}
