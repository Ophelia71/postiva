package com.postiva.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DevMediaConfigurationTest {
    @Test
    void usesPublicBackendUrlWhenNoLocalOverrideIsConfigured() throws IOException {
        MockEnvironment environment = devEnvironment()
                .withProperty("POSTIVA_PUBLIC_BASE_URL", "https://tunnel.example.test");

        assertEquals("https://tunnel.example.test", environment.getProperty("postiva.storage.public-base-url"));
    }

    @Test
    void explicitLocalUrlTakesPrecedence() throws IOException {
        MockEnvironment environment = devEnvironment()
                .withProperty("POSTIVA_PUBLIC_BASE_URL", "https://api.example.test")
                .withProperty("POSTIVA_LOCAL_PUBLIC_BASE_URL", "https://local.example.test");

        assertEquals("https://local.example.test", environment.getProperty("postiva.storage.public-base-url"));
    }

    @Test
    void noConfiguredPublicUrlKeepsLocalDevelopmentDefault() throws IOException {
        assertEquals("http://localhost:8080", devEnvironment().getProperty("postiva.storage.public-base-url"));
    }

    private MockEnvironment devEnvironment() throws IOException {
        MockEnvironment environment = new MockEnvironment();
        new YamlPropertySourceLoader().load("dev", new ClassPathResource("application-dev.yml"))
                .forEach(environment.getPropertySources()::addLast);
        return environment;
    }
}
