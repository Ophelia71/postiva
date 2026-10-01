package com.postiva.social.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetaOAuthStateServiceTest {
    @Test
    void multipleTabsKeepTheirOriginalUserEvenWhenCompletedOutOfOrder() {
        MetaOAuthStateService service = new MetaOAuthStateService();
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        String tabA = service.issue(userA);
        String tabB = service.issue(userB);
        String anotherTabA = service.issue(userA);

        assertEquals(userB, service.consume(tabB));
        assertEquals(userA, service.consume(anotherTabA));
        assertEquals(userA, service.consume(tabA));
        assertBadRequest(() -> service.consume(tabA));
    }

    @Test
    void stateIsLostWhenServiceInstanceIsReplaced() {
        String state = new MetaOAuthStateService().issue(UUID.randomUUID());
        assertBadRequest(() -> new MetaOAuthStateService().consume(state));
    }

    @Test
    void validStateReturnsBoundUserAndIsOneTimeUse() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        MetaOAuthStateService service = new MetaOAuthStateService(clock, new SecureRandom());
        UUID userId = UUID.randomUUID();

        String state = service.issue(userId);

        assertEquals(userId, service.consume(state));
        assertBadRequest(() -> service.consume(state));
    }

    @Test
    void issuedStatesAreStrongAndUnique() {
        MetaOAuthStateService service = new MetaOAuthStateService();

        String first = service.issue(UUID.randomUUID());
        String second = service.issue(UUID.randomUUID());

        assertNotEquals(first, second);
        assertTrue(first.length() >= 43);
        assertTrue(second.length() >= 43);
    }

    @Test
    void expiredStateIsRejected() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        MetaOAuthStateService service = new MetaOAuthStateService(clock, new SecureRandom());
        String state = service.issue(UUID.randomUUID());

        clock.advance(Duration.ofMinutes(11));

        assertBadRequest(() -> service.consume(state));
    }

    @Test
    void invalidOrMissingStateIsRejected() {
        MetaOAuthStateService service = new MetaOAuthStateService();

        assertBadRequest(() -> service.consume("not-issued"));
        assertBadRequest(() -> service.consume(null));
    }

    private void assertBadRequest(Runnable action) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, action::run);
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
