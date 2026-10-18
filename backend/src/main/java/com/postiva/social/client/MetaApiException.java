package com.postiva.social.client;

/** Preserves Meta's HTTP status so the scheduler can distinguish safe rejection from ambiguity. */
public final class MetaApiException extends RuntimeException {
    private final int statusCode;

    public MetaApiException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
