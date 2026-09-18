package com.postiva.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "postiva.openai")
public record OpenAiProperties(String apiKey, String model) {
    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}

