package com.postiva.common;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class RateLimitException extends ResponseStatusException {
    /** Why a request was limited, so callers such as the daily automation can react without parsing text. */
    public enum Reason { OTHER, AI_IN_FLIGHT, AI_PERIOD_QUOTA, AI_USER_DAILY, AI_GLOBAL_DAILY }

    private final long retryAfter;
    private final Reason reason;

    public RateLimitException(String message, long retryAfter) {
        this(message, retryAfter, Reason.OTHER);
    }

    public RateLimitException(String message, long retryAfter, Reason reason) {
        super(HttpStatus.TOO_MANY_REQUESTS, message);
        this.retryAfter = Math.max(1, retryAfter);
        this.reason = reason == null ? Reason.OTHER : reason;
    }

    public Reason limit() { return reason; }

    @Override public HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
        return headers;
    }
}
