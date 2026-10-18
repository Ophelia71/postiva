package com.postiva.social.client;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FacebookGraphClientDateTest {
    @Test
    void parsesFacebookCompactOffsetAndIsoOffsetAsTheSameInstant() {
        Instant expected = Instant.parse("2026-10-18T14:30:00Z");

        assertEquals(expected, FacebookGraphClient.parseDate("2026-10-18T14:30:00+0000"));
        assertEquals(expected, FacebookGraphClient.parseDate("2026-10-18T21:30:00+07:00"));
        assertNull(FacebookGraphClient.parseDate("invalid"));
    }
}
