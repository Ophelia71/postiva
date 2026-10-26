package com.postiva.automation;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutomationScheduleTest {
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final ZoneId NY = ZoneId.of("America/New_York");

    @Test
    void maskRoundTripsIsoDaysAndRejectsBadInput() {
        int mask = AutomationSchedule.toMask(List.of(1, 3, 7));
        assertEquals(List.of(1, 3, 7), AutomationSchedule.toDays(mask));
        assertTrue(AutomationSchedule.allows(mask, DayOfWeek.MONDAY));
        assertFalse(AutomationSchedule.allows(mask, DayOfWeek.TUESDAY));
        assertTrue(AutomationSchedule.allows(mask, DayOfWeek.SUNDAY));
        assertEquals(127, AutomationSchedule.toMask(List.of(1, 2, 3, 4, 5, 6, 7)));
        assertThrows(IllegalArgumentException.class, () -> AutomationSchedule.toMask(List.of()));
        assertThrows(IllegalArgumentException.class, () -> AutomationSchedule.toMask(List.of(0)));
        assertThrows(IllegalArgumentException.class, () -> AutomationSchedule.toMask(List.of(8)));
    }

    @Test
    void vietnameseSlotsAroundLocalMidnightKeepTheirLocalDate() {
        // 00:30 and 23:30 in Ho Chi Minh City (UTC+7, no DST) land on different UTC days.
        LocalDate day = LocalDate.of(2026, 10, 27);
        assertEquals(Instant.parse("2026-10-26T17:30:00Z"), AutomationSchedule.slot(day, LocalTime.of(0, 30), VN));
        assertEquals(Instant.parse("2026-10-27T16:30:00Z"), AutomationSchedule.slot(day, LocalTime.of(23, 30), VN));
    }

    @Test
    void springForwardGapMovesThePostForwardInsteadOfSkippingTheDay() {
        // 2026-03-08 02:30 does not exist in New York; the clock jumps from 02:00 to 03:00.
        Instant slot = AutomationSchedule.slot(LocalDate.of(2026, 3, 8), LocalTime.of(2, 30), NY);
        assertEquals(Instant.parse("2026-03-08T07:30:00Z"), slot);
    }

    @Test
    void fallBackOverlapUsesTheEarlierInstantOnce() {
        // 2026-11-01 01:30 happens twice in New York; the earlier one is still daylight time (UTC-4).
        Instant slot = AutomationSchedule.slot(LocalDate.of(2026, 11, 1), LocalTime.of(1, 30), NY);
        assertEquals(Instant.parse("2026-11-01T05:30:00Z"), slot);
    }

    @Test
    void upcomingSkipsDisabledWeekdaysAndStaysStrictlyInTheFuture() {
        int mondaysAndFridays = AutomationSchedule.toMask(List.of(1, 5));
        // Wednesday 2026-10-28 12:00 Vietnam time.
        Instant from = Instant.parse("2026-10-28T05:00:00Z");
        var slots = AutomationSchedule.upcoming(mondaysAndFridays, LocalTime.of(19, 0), VN, from, 3);
        assertEquals(List.of(LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 6)),
                slots.stream().map(AutomationSchedule.Planned::runDate).toList());
        // A slot exactly at "now" is not upcoming.
        Instant exactly = AutomationSchedule.slot(LocalDate.of(2026, 10, 30), LocalTime.of(19, 0), VN);
        assertEquals(LocalDate.of(2026, 11, 2),
                AutomationSchedule.upcoming(mondaysAndFridays, LocalTime.of(19, 0), VN, exactly, 1).getFirst().runDate());
    }

    @Test
    void dueWithinOnlyReturnsSlotsInsideTheLeadWindow() {
        int everyDay = 127;
        Instant now = Instant.parse("2026-10-27T05:00:00Z"); // 12:00 in Vietnam
        // 19:00 local is 7 hours away: inside a 12 hour window, outside a 6 hour window.
        assertEquals(1, AutomationSchedule.dueWithin(everyDay, LocalTime.of(19, 0), VN, now, 720).size());
        assertEquals(0, AutomationSchedule.dueWithin(everyDay, LocalTime.of(19, 0), VN, now, 360).size());
        assertEquals(LocalDate.of(2026, 10, 27),
                AutomationSchedule.dueWithin(everyDay, LocalTime.of(19, 0), VN, now, 720).getFirst().runDate());
    }
}
