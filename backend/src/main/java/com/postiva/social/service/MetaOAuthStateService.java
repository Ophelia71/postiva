package com.postiva.social.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MetaOAuthStateService {
    private static final Duration STATE_TTL = Duration.ofMinutes(10);
    private static final int STATE_BYTES = 32;

    private final ConcurrentHashMap<String, StateEntry> states = new ConcurrentHashMap<>();
    private final Clock clock;
    private final SecureRandom secureRandom;

    public MetaOAuthStateService() {
        this(Clock.systemUTC(), new SecureRandom());
    }

    MetaOAuthStateService(Clock clock, SecureRandom secureRandom) {
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    public String issue(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("OAuth state cần Postiva userId");
        }
        Instant now = clock.instant();
        states.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        byte[] randomBytes = new byte[STATE_BYTES];
        secureRandom.nextBytes(randomBytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        states.put(state, new StateEntry(userId, now.plus(STATE_TTL)));
        return state;
    }

    public UUID consume(String state) {
        StateEntry entry = state == null ? null : states.remove(state);
        if (entry == null || !entry.expiresAt().isAfter(clock.instant())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OAuth state không hợp lệ hoặc đã hết hạn");
        }
        return entry.userId();
    }

    private record StateEntry(UUID userId, Instant expiresAt) {
    }
}
