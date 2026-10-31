package com.postiva.calendar.service;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeeklyScheduleTest {
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    /** A Monday. */
    private static final LocalDate START = LocalDate.of(2026, 11, 2);
    private static final Instant BEFORE_WEEK = START.minusDays(1).atStartOfDay(VN).toInstant();

    @Test
    void sevenPostsFillTheWholeWeekAtTheGivenTime() {
        List<Instant> slots = WeeklySchedule.pick(START, 7, null, null, LocalTime.of(19, 0), VN, BEFORE_WEEK);

        assertEquals(7, slots.size());
        assertEquals(START.atTime(19, 0).atZone(VN).toInstant(), slots.getFirst());
    }

    @Test
    void shopChosenDaysComeFirstThenBestDaysThenEvenSpread() {
        List<Instant> slots = WeeklySchedule.pick(START, 3, List.of(3), List.of(5, 6), LocalTime.of(20, 0), VN, BEFORE_WEEK);

        assertEquals(List.of(DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY), weekdays(slots));
    }

    @Test
    void withoutSignalsPostsAreSpreadOut() {
        List<Instant> slots = WeeklySchedule.pick(START, 3, List.of(), List.of(), LocalTime.of(19, 0), VN, BEFORE_WEEK);

        // Positions 0, 2 and 5 of the week: never two posts on consecutive days.
        assertEquals(List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY), weekdays(slots));
    }

    @Test
    void slotsTooCloseToNowAreDroppedAndReplacedLaterInTheWeek() {
        // 18:45 on the Monday: 19:00 is less than 30 minutes away.
        Instant now = START.atTime(18, 45).atZone(VN).toInstant();

        List<Instant> slots = WeeklySchedule.pick(START, 7, null, null, LocalTime.of(19, 0), VN, now);

        assertEquals(6, slots.size());
        assertEquals(DayOfWeek.TUESDAY, slots.getFirst().atZone(VN).getDayOfWeek());
    }

    @Test
    void weekEntirelyInThePastGivesNoSlots() {
        Instant now = START.plusDays(8).atStartOfDay(VN).toInstant();

        assertEquals(List.of(), WeeklySchedule.pick(START, 3, null, null, LocalTime.of(19, 0), VN, now));
    }

    private static List<DayOfWeek> weekdays(List<Instant> slots) {
        return slots.stream().map(slot -> slot.atZone(VN).getDayOfWeek()).toList();
    }
}
