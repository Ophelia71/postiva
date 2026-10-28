package com.postiva.calendar.service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Picks which days of a week get a post and at what time. Pure so the rules can be tested directly. */
final class WeeklySchedule {
    static final LocalTime DEFAULT_TIME = LocalTime.of(19, 0);
    /** A slot this close to now cannot be reviewed and approved in time, so it is dropped. */
    static final Duration MIN_LEAD = Duration.ofMinutes(30);

    private WeeklySchedule() {
    }

    /**
     * Order of preference: days the shop chose, then the days its past posts did best on, then days spread evenly
     * across the week. Slots already (nearly) in the past are skipped, so the result can be shorter than asked.
     */
    static List<Instant> pick(LocalDate start, int count, List<Integer> preferredDays, List<Integer> bestDays,
                              LocalTime time, ZoneId zone, Instant now) {
        List<LocalDate> week = new ArrayList<>();
        for (int offset = 0; offset < 7; offset++) {
            LocalDate day = start.plusDays(offset);
            if (day.atTime(time).atZone(zone).toInstant().isAfter(now.plus(MIN_LEAD))) week.add(day);
        }

        Set<LocalDate> chosen = new LinkedHashSet<>();
        addWeekdays(chosen, week, preferredDays, count);
        addWeekdays(chosen, week, bestDays, count);
        // Evenly spaced picks keep gaps between posts when there is no stronger signal.
        for (int index = 0; chosen.size() < count && index < count; index++) {
            int position = (int) Math.round(index * (double) week.size() / count);
            if (position < week.size()) chosen.add(week.get(position));
        }
        for (LocalDate day : week) {
            if (chosen.size() >= count) break;
            chosen.add(day);
        }
        return chosen.stream().sorted().map(day -> day.atTime(time).atZone(zone).toInstant()).toList();
    }

    private static void addWeekdays(Set<LocalDate> chosen, List<LocalDate> week, List<Integer> weekdays, int count) {
        if (weekdays == null) return;
        for (Integer weekday : weekdays) {
            if (chosen.size() >= count) return;
            week.stream().filter(day -> day.getDayOfWeek().getValue() == weekday).findFirst().ifPresent(chosen::add);
        }
    }
}
