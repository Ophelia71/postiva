package com.postiva.social.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MetaOAuthStateService {
    private final ConcurrentHashMap<String, StateEntry> states = new ConcurrentHashMap<>();

    public String issue(UUID userId) {
        states.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(Instant.now()));
        String state = UUID.randomUUID().toString();
        states.put(state, new StateEntry(userId, Instant.now().plus(10, ChronoUnit.MINUTES)));
        return state;
    }

    public UUID consume(String state) {
        StateEntry entry = state == null ? null : states.remove(state);
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Meta OAuth state không hợp lệ hoặc đã hết hạn");
        }
        return entry.userId();
    }

    private record StateEntry(UUID userId, Instant expiresAt) {
    }
}
