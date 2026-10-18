package com.postiva.usage;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class AiRequestConflictException extends ResponseStatusException {
    private final String state;
    public AiRequestConflictException(String state, String message) { super(HttpStatus.CONFLICT, message); this.state = state; }
    @Override public HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-AI-Request-State", state);
        return headers;
    }
}
