package com.postiva.automation;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Pure schedule maths for daily rules. A rule stores a wall-clock time and an IANA zone; the
 * instant is derived for each calendar day so daylight-saving changes never drift the post.
 * Days of week are an ISO bit mask: bit 0 = Monday ... bit 6 = Sunday (127 = every day).
 */
public final class AutomationSchedule {
    private AutomationSchedule() { }

    public record Planned(LocalDate runDate, Instant at) { }

    public static int toMask(Collection<Integer> isoDays) {
        int mask = 0;
        for (Integer day : isoDays) {
            if (day == null || day < 1 || day > 7) throw new IllegalArgumentException("Ngày trong tuần không hợp lệ");
            mask |= 1 << (day - 1);
        }
        if (mask == 0) throw new IllegalArgumentException("Chọn ít nhất một ngày trong tuần");
        return mask;
    }

    public static List<Integer> toDays(int mask) {
        List<Integer> days = new ArrayList<>();
        for (int day = 1; day <= 7; day++) {
            if ((mask & (1 << (day - 1))) != 0) days.add(day);
        }
        return days;
    }

    public static boolean allows(int mask, DayOfWeek day) {
        return (mask & (1 << (day.getValue() - 1))) != 0;
    }

    /**
     * The instant of {@code time} on {@code date} in {@code zone}. In a DST gap the time moves
     * forward by the gap; in an overlap the earlier of the two instants is used.
     */
    public static Instant slot(LocalDate date, LocalTime time, ZoneId zone) {
        return ZonedDateTime.of(date, time, zone).toInstant();
    }

    /** Slots strictly after {@code from}, earliest first, at most {@code count}. */
    public static List<Planned> upcoming(int mask, LocalTime time, ZoneId zone, Instant from, int count) {
        List<Planned> result = new ArrayList<>();
        LocalDate date = from.atZone(zone).toLocalDate().minusDays(1);
        for (int offset = 0; offset < 400 && result.size() < count; offset++, date = date.plusDays(1)) {
            if (!allows(mask, date.getDayOfWeek())) continue;
            Instant at = slot(date, time, zone);
            if (at.isAfter(from)) result.add(new Planned(date, at));
        }
        return result;
    }

    /** Slots that fall inside the lead window, i.e. the posts the planner should prepare now. */
    public static List<Planned> dueWithin(int mask, LocalTime time, ZoneId zone, Instant now, long leadMinutes) {
        Instant limit = now.plusSeconds(leadMinutes * 60);
        List<Planned> result = new ArrayList<>();
        for (Planned planned : upcoming(mask, time, zone, now, 3)) {
            if (!planned.at().isAfter(limit)) result.add(planned);
        }
        return result;
    }
}
