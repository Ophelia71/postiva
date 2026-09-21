package com.postiva.scheduler.repository;

import com.postiva.scheduler.entity.PublishLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

public interface PublishLogRepository extends JpaRepository<PublishLog, UUID> {
    @org.springframework.data.jpa.repository.Query("select l from PublishLog l where l.scheduledPostId in (select s.id from ScheduledPost s where s.socialAccountId in (select a.id from SocialAccount a where a.userId = :userId and a.disconnectedAt is null)) order by l.publishedAt desc")
    List<PublishLog> findAllForUser(@org.springframework.data.repository.query.Param("userId") UUID userId);

    Optional<PublishLog> findFirstByProviderAndExternalPostId(String provider, String externalPostId);

    Optional<PublishLog> findFirstByScheduledPostId(UUID scheduledPostId);

    Optional<PublishLog> findFirstByScheduledPostIdAndStatusOrderByPublishedAtDesc(UUID scheduledPostId, String status);

    void deleteByScheduledPostId(UUID scheduledPostId);
}
