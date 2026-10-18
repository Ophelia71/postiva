package com.postiva.social.service;

import com.postiva.common.Platform;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.UUID;

/** Builds Meta callback URLs from the configured public HTTPS origin, never from the request Host header. */
@Component
public class MetaDeletionStatusUrlFactory {
    private final String publicBaseUrl;

    public MetaDeletionStatusUrlFactory(@Value("${postiva.meta.public-base-url:}") String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.trim();
    }

    public void requireConfigured() {
        try {
            URI uri = URI.create(publicBaseUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw unavailable();
            }
        } catch (IllegalArgumentException exception) {
            throw unavailable();
        }
    }

    public String statusUrl(Platform provider, String confirmationCode) {
        requireConfigured();
        UUID.fromString(confirmationCode);
        String route = switch (provider) {
            case FACEBOOK -> "meta";
            case INSTAGRAM -> "instagram";
            case THREADS -> "threads";
        };
        return publicBaseUrl.replaceAll("/+$", "") + "/api/social/" + route
                + "/delete-data/status/" + confirmationCode;
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Chưa cấu hình POSTIVA_META_PUBLIC_BASE_URL là domain HTTPS công khai.");
    }
}
