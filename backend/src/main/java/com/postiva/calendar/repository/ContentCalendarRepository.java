package com.postiva.calendar.repository;

import com.postiva.calendar.entity.ContentCalendar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;

public interface ContentCalendarRepository extends JpaRepository<ContentCalendar, UUID> {
    Optional<ContentCalendar> findByIdAndUserId(UUID id, UUID userId);
}
