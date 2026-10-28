package com.postiva.calendar.repository;

import com.postiva.calendar.entity.CalendarItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CalendarItemRepository extends JpaRepository<CalendarItem, UUID> {
    List<CalendarItem> findByCalendarIdOrderByPlannedDateAsc(UUID calendarId);

    Optional<CalendarItem> findByIdAndCalendarId(UUID id, UUID calendarId);
}
