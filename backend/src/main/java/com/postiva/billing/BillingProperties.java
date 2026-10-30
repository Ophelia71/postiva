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
    // Plain bank transfer to the shop's own account, confirmed by an admin (no payment provider needed).
    private String bankCode = "TPB";
    private String bankAccountNo = "";
    private String bankAccountName = "";
    // SePay watches the bank account and reports incoming transfers, so they confirm themselves.
    private String sepayApiKey = "";
    // SePay's user API token: lets Postiva ask for the account's incoming transfers itself.
    private String sepayApiToken = "";

    public boolean ready() {
        return enabled && proPriceVnd >= 2_000 && proPriceVnd <= 100_000_000
                && providerConfigured() && https(refundPolicyUrl);
    }

    /** Bank transfer sells whenever an account is set; payOS, when also ready, takes precedence. */
    public boolean bankTransferReady() {
        return bankAccountNo.matches("[0-9]{6,20}") && !bankAccountName.isBlank() && bankCode.matches("[A-Za-z0-9]{2,15}")
                && proPriceVnd >= 2_000 && proPriceVnd <= 100_000_000 && https(refundPolicyUrl);
    }

    /** Existing paid orders must still be serviced when sales are paused or the published price changes. */
    public boolean providerConfigured() {
        return !clientId.isBlank() && !apiKey.isBlank() && checksumKey.length() >= 32
                && https(returnUrl) && https(cancelUrl);
    }

    /** https everywhere; plain http only for this machine, so payOS can be tried against a local frontend. */
    private boolean https(String value) {
        try {
            URI uri = URI.create(value);
            if (uri.getHost() == null) return false;
            if ("https".equalsIgnoreCase(uri.getScheme())) return true;
            return "http".equalsIgnoreCase(uri.getScheme())
                    && ("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
        }
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
    public String getSepayApiKey() { return sepayApiKey; }
    public String getSepayApiToken() { return sepayApiToken; }
    public void setSepayApiToken(String sepayApiToken) { this.sepayApiToken = sepayApiToken == null ? "" : sepayApiToken.trim(); }
    public void setSepayApiKey(String sepayApiKey) { this.sepayApiKey = sepayApiKey == null ? "" : sepayApiKey.trim(); }
    public String getBankCode() { return bankCode; }
    public void setBankCode(String bankCode) { this.bankCode = bankCode == null ? "" : bankCode.trim(); }
    public String getBankAccountNo() { return bankAccountNo; }
    public void setBankAccountNo(String bankAccountNo) { this.bankAccountNo = bankAccountNo == null ? "" : bankAccountNo.replace(" ", ""); }
    public String getBankAccountName() { return bankAccountName; }
    public void setBankAccountName(String bankAccountName) { this.bankAccountName = bankAccountName == null ? "" : bankAccountName.trim(); }
}
