package com.postiva.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@Profile({"staging", "prod"})
public class DeploymentConfigurationValidator implements ApplicationRunner {
    private final String jwtSecret;
    private final String tokenEncryptionKey;
    private final String mfaEncryptionKey;
    private final String mediaSigningSecret;
    private final String frontendOrigin;
    private final String additionalCorsOrigins;
    private final String trustedProxyAddresses;
    private final String publicBaseUrl;
    private final String metaPublicBaseUrl;
    private final boolean mailEnabled;
    private final boolean mailLogCodes;
    private final String mailUsername;
    private final String mailPassword;
    private final String mailFrom;
    private final boolean aiEnabled;
    private final String openAiKey;
    private final boolean billingEnabled;
    private final long proPriceVnd;
    private final String payOsClientId;
    private final String payOsApiKey;
    private final String payOsChecksumKey;
    private final String billingReturnUrl;
    private final String billingCancelUrl;
    private final String billingRefundPolicyUrl;

    public DeploymentConfigurationValidator(
            @Value("${app.jwt.secret}") String jwtSecret,
            @Value("${postiva.security.token-encryption-key}") String tokenEncryptionKey,
            @Value("${postiva.security.mfa-encryption-key}") String mfaEncryptionKey,
            @Value("${postiva.storage.signing-secret}") String mediaSigningSecret,
            @Value("${postiva.frontend-origin}") String frontendOrigin,
            @Value("${postiva.cors.allowed-origin-patterns:}") String additionalCorsOrigins,
            @Value("${postiva.security.trusted-proxy-addresses}") String trustedProxyAddresses,
            @Value("${postiva.storage.public-base-url}") String publicBaseUrl,
            @Value("${postiva.meta.public-base-url}") String metaPublicBaseUrl,
            @Value("${postiva.mail.enabled}") boolean mailEnabled,
            @Value("${spring.mail.username:}") String mailUsername,
            @Value("${spring.mail.password:}") String mailPassword,
            @Value("${postiva.mail.from}") String mailFrom,
            @Value("${postiva.mail.log-codes:false}") boolean mailLogCodes,
            @Value("${postiva.ai.cost.enabled:true}") boolean aiEnabled,
            @Value("${postiva.openai.api-key:}") String openAiKey,
            @Value("${postiva.billing.enabled:false}") boolean billingEnabled,
            @Value("${postiva.billing.pro-price-vnd:0}") long proPriceVnd,
            @Value("${postiva.billing.client-id:}") String payOsClientId,
            @Value("${postiva.billing.api-key:}") String payOsApiKey,
            @Value("${postiva.billing.checksum-key:}") String payOsChecksumKey,
            @Value("${postiva.billing.return-url:}") String billingReturnUrl,
            @Value("${postiva.billing.cancel-url:}") String billingCancelUrl,
            @Value("${postiva.billing.refund-policy-url:}") String billingRefundPolicyUrl
    ) {
        this.jwtSecret = jwtSecret;
        this.tokenEncryptionKey = tokenEncryptionKey;
        this.mfaEncryptionKey = mfaEncryptionKey;
        this.mediaSigningSecret = mediaSigningSecret;
        this.frontendOrigin = frontendOrigin;
        this.additionalCorsOrigins = additionalCorsOrigins;
        this.trustedProxyAddresses = trustedProxyAddresses;
        this.publicBaseUrl = publicBaseUrl;
        this.metaPublicBaseUrl = metaPublicBaseUrl;
        this.mailEnabled = mailEnabled;
        this.mailLogCodes = mailLogCodes;
        this.mailUsername = mailUsername;
        this.mailPassword = mailPassword;
        this.mailFrom = mailFrom;
        this.aiEnabled = aiEnabled;
        this.openAiKey = openAiKey;
        this.billingEnabled = billingEnabled;
        this.proPriceVnd = proPriceVnd;
        this.payOsClientId = payOsClientId;
        this.payOsApiKey = payOsApiKey;
        this.payOsChecksumKey = payOsChecksumKey;
        this.billingReturnUrl = billingReturnUrl;
        this.billingCancelUrl = billingCancelUrl;
        this.billingRefundPolicyUrl = billingRefundPolicyUrl;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> errors = new ArrayList<>();
        requireKey("POSTIVA_JWT_SECRET", jwtSecret, errors);
        requireKey("POSTIVA_TOKEN_ENCRYPTION_KEY", tokenEncryptionKey, errors);
        requireKey("POSTIVA_MFA_ENCRYPTION_KEY", mfaEncryptionKey, errors);
        requireKey("POSTIVA_MEDIA_SIGNING_SECRET", mediaSigningSecret, errors);
        requireOrigin("POSTIVA_FRONTEND_ORIGIN", frontendOrigin, errors);
        requireHttps("POSTIVA_PUBLIC_BASE_URL", publicBaseUrl, errors);
        requireHttps("POSTIVA_META_PUBLIC_BASE_URL", metaPublicBaseUrl, errors);

        for (String origin : additionalCorsOrigins.split(",")) {
            String value = origin.trim();
            if (value.contains("*")) {
                errors.add("POSTIVA_CORS_ALLOWED_ORIGIN_PATTERNS must not contain wildcard origins");
                break;
            }
            if (StringUtils.hasText(value)) requireOrigin("POSTIVA_CORS_ALLOWED_ORIGIN_PATTERNS", value, errors);
        }
        List<String> proxies = Arrays.stream(trustedProxyAddresses.split(","))
                .map(String::trim).filter(StringUtils::hasText).toList();
        if (proxies.isEmpty()) {
            errors.add("POSTIVA_TRUSTED_PROXY_ADDRESSES must list the trusted reverse proxy addresses/CIDRs");
        } else {
            proxies.forEach(address -> validateProxy(address, errors));
        }
        if (mailEnabled && (!StringUtils.hasText(mailUsername)
                || !StringUtils.hasText(mailPassword) || !StringUtils.hasText(mailFrom))) {
            errors.add("MAIL_USERNAME, MAIL_PASSWORD and MAIL_FROM are required when POSTIVA_MAIL_ENABLED=true");
        }
        if (mailLogCodes) {
            errors.add("POSTIVA_MAIL_LOG_CODES writes sign-up codes to the log and is for local development only");
        }
        if (aiEnabled && !StringUtils.hasText(openAiKey)) {
            errors.add("OPENAI_API_KEY is required while POSTIVA_AI_ENABLED=true");
        }
        if (billingEnabled) {
            if (!StringUtils.hasText(payOsClientId) || !StringUtils.hasText(payOsApiKey)) {
                errors.add("PAYOS_CLIENT_ID and PAYOS_API_KEY are required while POSTIVA_BILLING_ENABLED=true");
            }
            requireKey("PAYOS_CHECKSUM_KEY", payOsChecksumKey, errors);
            if (proPriceVnd < 2_000 || proPriceVnd > 100_000_000) {
                errors.add("POSTIVA_PRO_PRICE_VND must be between 2000 and 100000000 when billing is enabled");
            }
            requireHttps("POSTIVA_BILLING_RETURN_URL", billingReturnUrl, errors);
            requireHttps("POSTIVA_BILLING_CANCEL_URL", billingCancelUrl, errors);
            requireHttps("POSTIVA_BILLING_REFUND_POLICY_URL", billingRefundPolicyUrl, errors);
        }

        if (!errors.isEmpty()) {
            throw new IllegalStateException("Invalid staging/production configuration: " + String.join("; ", errors));
        }
    }

    private static void requireKey(String name, String value, List<String> errors) {
        if (value == null || value.getBytes(StandardCharsets.UTF_8).length < 32) {
            errors.add(name + " must contain at least 32 UTF-8 bytes");
        }
    }

    private static void requireHttps(String name, String value, List<String> errors) {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !StringUtils.hasText(uri.getHost())
                    || uri.getUserInfo() != null) {
                errors.add(name + " must be an https URL with a hostname and no embedded credentials");
            }
        } catch (RuntimeException exception) {
            errors.add(name + " must be a valid https URL");
        }
    }

    private static void requireOrigin(String name, String value, List<String> errors) {
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !StringUtils.hasText(uri.getHost())
                    || uri.getUserInfo() != null || StringUtils.hasText(uri.getRawPath())
                    || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                errors.add(name + " must be an exact https origin without path, query, fragment or credentials");
            }
        } catch (RuntimeException exception) {
            errors.add(name + " must be a valid exact https origin");
        }
    }

    private static void validateProxy(String address, List<String> errors) {
        try {
            new IpAddressMatcher(address);
        } catch (IllegalArgumentException exception) {
            errors.add("POSTIVA_TRUSTED_PROXY_ADDRESSES contains an invalid IP/CIDR entry");
        }
    }
}
