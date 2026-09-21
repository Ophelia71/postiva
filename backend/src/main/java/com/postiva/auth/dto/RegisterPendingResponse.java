package com.postiva.auth.dto;

public record RegisterPendingResponse(
        String email,
        long expiresInSeconds
) {
}
