package com.postiva.social.controller;

import com.postiva.social.service.MetaOAuthService;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

final class OAuthCallbackRedirects {
    private OAuthCallbackRedirects() {
    }

    static URI connected(String frontendOrigin, String provider) {
        return base(frontendOrigin)
                .queryParam(provider, "connected")
                .build()
                .encode()
                .toUri();
    }

    static URI connectedFacebook(String frontendOrigin, int pageCount) {
        return base(frontendOrigin)
                .queryParam("facebook", "connected")
                .queryParam("pages", pageCount)
                .build()
                .encode()
                .toUri();
    }

    static URI error(String frontendOrigin, String provider, String reason) {
        return base(frontendOrigin)
                .queryParam(provider, "error")
                .queryParam("reason", reason)
                .build()
                .encode()
                .toUri();
    }

    static String failureReason(RuntimeException exception) {
        if (exception instanceof MetaOAuthService.NoFacebookPagesException) {
            return "no_pages";
        }
        if (exception instanceof ResponseStatusException statusException
                && statusException.getStatusCode() == HttpStatus.BAD_REQUEST) {
            return "invalid_state";
        }
        return "oauth_failed";
    }

    static String providerErrorReason(String providerError) {
        return "access_denied".equalsIgnoreCase(providerError)
                ? "access_denied"
                : "provider_error";
    }

    static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static UriComponentsBuilder base(String frontendOrigin) {
        return UriComponentsBuilder
                .fromUriString(frontendOrigin.replaceAll("/+$", ""))
                .path("/app/connections");
    }
}
