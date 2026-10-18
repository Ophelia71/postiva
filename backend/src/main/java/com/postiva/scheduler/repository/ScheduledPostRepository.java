package com.postiva.scheduler.repository;

import com.postiva.scheduler.ScheduledPostStatus;
import com.postiva.scheduler.entity.ScheduledPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScheduledPostRepository extends JpaRepository<ScheduledPost, UUID> {
    List<ScheduledPost> findTop20ByStatusAndScheduledTimeLessThanEqualOrderByScheduledTimeAsc(
            ScheduledPostStatus status, LocalDateTime time
    );

    List<ScheduledPost> findBySocialAccountIdAndStatus(UUID socialAccountId, ScheduledPostStatus status);
    List<ScheduledPost> findBySocialAccountId(UUID socialAccountId);

    Optional<ScheduledPost> findByIdAndClaimTokenAndStatus(
            UUID id, UUID claimToken, ScheduledPostStatus status
    );

    List<ScheduledPost> findByPostId(UUID postId);

    @Query("""
            select scheduled from ScheduledPost scheduled
            where scheduled.status = :status and scheduled.postId in (
                select post.id from GeneratedPost post where post.userId = :userId
            )
            """)
    List<ScheduledPost> findByUserIdAndStatus(@Param("userId") UUID userId,
                                            @Param("status") ScheduledPostStatus status);

    @Query("""
            select scheduled from ScheduledPost scheduled
            where scheduled.socialAccountId in (
                select account.id from SocialAccount account
                where account.userId = :userId and account.disconnectedAt is null
            )
            order by scheduled.scheduledTime desc
            """)
    List<ScheduledPost> findAllForUser(@Param("userId") UUID userId);

    @Query("""
            select scheduled from ScheduledPost scheduled
            where scheduled.socialAccountId in (
                select account.id from SocialAccount account where account.userId = :userId
            )
            order by scheduled.scheduledTime desc
            """)
    List<ScheduledPost> findAllIncludingDisconnectedForUser(@Param("userId") UUID userId);

    @Query("""
            select scheduled from ScheduledPost scheduled
            where scheduled.id = :id and scheduled.socialAccountId in (
                select account.id from SocialAccount account
                where account.userId = :userId and account.disconnectedAt is null
            )
            """)
    Optional<ScheduledPost> findByIdForUser(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("""
            select scheduled from ScheduledPost scheduled
            where scheduled.id = :id and scheduled.socialAccountId in (
                select account.id from SocialAccount account where account.userId = :userId
            )
            """)
    Optional<ScheduledPost> findByIdIncludingDisconnectedForUser(
            @Param("id") UUID id, @Param("userId") UUID userId
    );
}
