package com.postiva.usage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How many AI requests a person may make in a day, by plan. */
class UsagePropertiesTest {
    private final UsageProperties properties = new UsageProperties();

    @Test
    void proGetsMoreRequestsPerDayThanFree() {
        assertEquals(10, properties.dailyAttemptsFor("FREE"));
        assertEquals(60, properties.dailyAttemptsFor("PRO"));
        assertTrue(properties.dailyAttemptsFor("PRO") > properties.dailyAttemptsFor("FREE"));
    }

    @Test
    void anUnknownPlanFallsBackToTheFreeLimit() {
        assertEquals(properties.getFreeDailyAttempts(), properties.dailyAttemptsFor("TRIAL"));
        assertEquals(properties.getFreeDailyAttempts(), properties.dailyAttemptsFor(null));
    }

    @Test
    void limitsCanBeChanged() {
        properties.setProDailyAttempts(100);
        properties.setFreeDailyAttempts(5);

        assertEquals(100, properties.dailyAttemptsFor("PRO"));
        assertEquals(5, properties.dailyAttemptsFor("FREE"));
    }
}
