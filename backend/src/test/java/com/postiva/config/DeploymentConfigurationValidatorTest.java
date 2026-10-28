package com.postiva.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeploymentConfigurationValidatorTest {
    @Test
    void acceptsCompleteSecureConfiguration() {
        assertDoesNotThrow(() -> validConfiguration().run(null));
    }

    @Test
    void rejectsWeakKeysWildcardCorsAndMissingTrustedProxyWithoutPrintingValues() {
        DeploymentConfigurationValidator validator = new DeploymentConfigurationValidator(
                "short-jwt", "short-token", "short-mfa", "short-media",
                "https://postiva.example", "https://*.example",
                "", "https://postiva.example", "https://postiva.example", true,
                "", "", "", false, true, "", true, 0,
                "", "", "short-checksum", "http://return.example", "", ""
        );

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> validator.run(null));

        assertFalse(exception.getMessage().contains("short-jwt"));
        assertFalse(exception.getMessage().contains("short-token"));
        assertFalse(exception.getMessage().contains("short-mfa"));
        assertFalse(exception.getMessage().contains("short-checksum"));
    }

    private DeploymentConfigurationValidator validConfiguration() {
        return validator(false);
    }

    @Test
    void localCodeLoggingIsRejectedOnServers() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> validator(true).run(null));
        assertTrue(exception.getMessage().contains("POSTIVA_MAIL_LOG_CODES"));
    }

    private DeploymentConfigurationValidator validator(boolean mailLogCodes) {
        return new DeploymentConfigurationValidator(
                "j".repeat(32), "t".repeat(32), "m".repeat(32), "s".repeat(32),
                "https://postiva.example", "", "172.30.0.2/32", "https://postiva.example",
                "https://postiva.example", true, "sender@example.test", "provider-app-password",
                "no-reply@example.test", mailLogCodes, true, "openai-project-key", false, 0,
                "", "", "", "", "", ""
        );
    }
}
