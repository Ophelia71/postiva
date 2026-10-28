package com.postiva.calendar.repository;

import com.postiva.calendar.entity.ContentCalendar;
import com.postiva.calendar.entity.ContentCalendarStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContentCalendarRepository extends JpaRepository<ContentCalendar, UUID> {
    Optional<ContentCalendar> findByIdAndUserId(UUID id, UUID userId);

    /** Weekly plans only: template calendars from the old generator have no social account. */
    List<ContentCalendar> findTop10ByUserIdAndSocialAccountIdIsNotNullOrderByCreatedAtDesc(UUID userId);

    boolean existsByUserIdAndStatus(UUID userId, ContentCalendarStatus status);
}
