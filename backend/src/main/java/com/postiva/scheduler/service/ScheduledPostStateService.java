package com.postiva.scheduler.service;

import com.postiva.common.PostStatus;
import com.postiva.scheduler.ScheduledPostStatus;
import com.postiva.scheduler.entity.PublishLog;
import com.postiva.scheduler.entity.ScheduledPost;
import com.postiva.scheduler.repository.PublishLogRepository;
import com.postiva.scheduler.repository.ScheduledPostRepository;
import com.postiva.post.entity.GeneratedPost;
import com.postiva.post.repository.GeneratedPostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ScheduledPostStateService {
    private static final Logger log = LoggerFactory.getLogger(ScheduledPostStateService.class);
    private final ScheduledPostRepository schedules;
    private final PublishLogRepository logs;
    private final GeneratedPostRepository posts;
    private final JdbcTemplate jdbc;

    public ScheduledPostStateService(ScheduledPostRepository schedules,
                                     PublishLogRepository logs,
                                     GeneratedPostRepository posts,
                                     JdbcTemplate jdbc) {
        this.schedules = schedules;
        this.logs = logs;
        this.posts = posts;
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean complete(Claim claim, String externalPostId, Instant publishedAt) {
        Optional<ScheduledPost> item = claimed(claim);
        if (item.isEmpty()) return false;

        ScheduledPost scheduled = item.get();
        scheduled.setStatus(ScheduledPostStatus.PUBLISHED);
        scheduled.setPublishedAt(publishedAt);
        scheduled.setLastError(null);
        clearClaim(scheduled);
        schedules.save(scheduled);
        saveLog(scheduled, "SUCCESS", externalPostId, null, publishedAt);
        refreshPostStatus(scheduled.getPostId());
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean retryOrFail(Claim claim, String error, boolean retryable,
                               int maxAttempts, Instant nextAttemptAt) {
        Optional<ScheduledPost> item = claimed(claim);
        if (item.isEmpty()) return false;

        ScheduledPost scheduled = item.get();
        boolean canRetry = retryable && scheduled.getAttemptCount() < maxAttempts;
        scheduled.setStatus(canRetry ? ScheduledPostStatus.SCHEDULED : ScheduledPostStatus.FAILED);
        scheduled.setNextAttemptAt(canRetry ? nextAttemptAt : null);
        scheduled.setLastError(error);
        clearClaim(scheduled);
        schedules.save(scheduled);
        saveLog(scheduled, canRetry ? "RETRY_SCHEDULED" : "FAILED", null, error, null);
        refreshPostStatus(scheduled.getPostId());
        if (canRetry) {
            log.warn("Scheduled publish retry scheduled (scheduleId={}, attemptCount={}, nextAttemptAt={})",
                    scheduled.getId(), scheduled.getAttemptCount(), scheduled.getNextAttemptAt());
        } else {
            log.error("Scheduled publish permanently failed (scheduleId={}, attemptCount={})",
                    scheduled.getId(), scheduled.getAttemptCount());
        }
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markUnknown(Claim claim, String error) {
        Optional<ScheduledPost> item = claimed(claim);
        if (item.isEmpty()) return false;

        ScheduledPost scheduled = item.get();
        scheduled.setStatus(ScheduledPostStatus.UNKNOWN);
        scheduled.setLastError(error);
        scheduled.setNextAttemptAt(null);
        clearClaim(scheduled);
        schedules.save(scheduled);
        saveLog(scheduled, "UNKNOWN", null, error, null);
        refreshPostStatus(scheduled.getPostId());
        log.error("Scheduled publish outcome is unknown and needs operator review (scheduleId={}, attemptCount={})",
                scheduled.getId(), scheduled.getAttemptCount());
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean cancel(Claim claim, String reason) {
        Optional<ScheduledPost> item = claimed(claim);
        if (item.isEmpty()) return false;

        ScheduledPost scheduled = item.get();
        scheduled.setStatus(ScheduledPostStatus.CANCELLED);
        scheduled.setLastError(reason);
        clearClaim(scheduled);
        schedules.save(scheduled);
        refreshPostStatus(scheduled.getPostId());
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recoverExpiredClaims(Instant now) {
        List<ExpiredClaim> expired = jdbc.query("""
                UPDATE scheduled_posts
                SET status = 'UNKNOWN',
                    last_error = 'Kết quả đăng chưa xác định do worker dừng hoặc quá thời gian giữ lease. Hãy kiểm tra bài trên nền tảng trước khi thử lại.',
                    claim_token = NULL,
                    lease_until = NULL,
                    updated_at = ?
                WHERE status = 'PUBLISHING' AND lease_until <= ?
                RETURNING id, post_id, platform
                """, (rs, row) -> new ExpiredClaim(
                rs.getObject("id", UUID.class),
                rs.getObject("post_id", UUID.class),
                rs.getString("platform")
        ), Timestamp.from(now), Timestamp.from(now));

        for (ExpiredClaim claim : expired) {
            saveLog(claim.id(), claim.platform(), "UNKNOWN", null,
                    "Worker hết lease; cần đối soát thủ công.", null);
            refreshPostStatus(claim.postId());
        }
        return expired.size();
    }

    @Transactional
    public void confirmPublished(ScheduledPost scheduled, String externalPostId, Instant publishedAt) {
        scheduled.setStatus(ScheduledPostStatus.PUBLISHED);
        scheduled.setPublishedAt(publishedAt);
        scheduled.setLastError(null);
        scheduled.setNextAttemptAt(null);
        clearClaim(scheduled);
        schedules.save(scheduled);
        saveLog(scheduled, "SUCCESS", externalPostId,
                "Đã xác nhận thủ công sau khi kiểm tra nền tảng.", publishedAt);
        refreshPostStatus(scheduled.getPostId());
    }

    @Transactional
    public void confirmNotPublished(ScheduledPost scheduled) {
        scheduled.setStatus(ScheduledPostStatus.FAILED);
        scheduled.setLastError("Người dùng xác nhận đã kiểm tra nền tảng và bài chưa được đăng. Có thể thử lại thủ công.");
        scheduled.setNextAttemptAt(null);
        clearClaim(scheduled);
        schedules.save(scheduled);
        saveLog(scheduled, "CONFIRMED_NOT_PUBLISHED", null, scheduled.getLastError(), null);
        refreshPostStatus(scheduled.getPostId());
    }

    @Transactional
    public void refreshPostStatus(UUID postId) {
        GeneratedPost post = posts.findById(postId).orElse(null);
        if (post == null) return;

        List<ScheduledPost> all = schedules.findByPostId(postId);
        boolean pending = all.stream().anyMatch(item ->
                item.getStatus() == ScheduledPostStatus.SCHEDULED
                        || item.getStatus() == ScheduledPostStatus.PUBLISHING);
        boolean published = all.stream().anyMatch(item -> item.getStatus() == ScheduledPostStatus.PUBLISHED);
        if (pending) {
            post.setStatus(PostStatus.SCHEDULED);
        } else if (published) {
            post.setStatus(PostStatus.PUBLISHED);
        } else if (!all.isEmpty()) {
            boolean failed = all.stream().anyMatch(item ->
                    item.getStatus() == ScheduledPostStatus.FAILED
                            || item.getStatus() == ScheduledPostStatus.UNKNOWN);
            post.setStatus(failed ? PostStatus.FAILED : PostStatus.DRAFT);
        }
        posts.save(post);
    }

    private Optional<ScheduledPost> claimed(Claim claim) {
        return schedules.findByIdAndClaimTokenAndStatus(
                claim.scheduledPostId(), claim.claimToken(), ScheduledPostStatus.PUBLISHING);
    }

    private void clearClaim(ScheduledPost scheduled) {
        scheduled.setClaimToken(null);
        scheduled.setLeaseUntil(null);
    }

    private void saveLog(ScheduledPost scheduled, String status, String externalPostId,
                         String error, Instant publishedAt) {
        saveLog(scheduled.getId(), scheduled.getPlatform().name(), status, externalPostId, error, publishedAt);
    }

    private void saveLog(UUID scheduledPostId, String provider, String status,
                         String externalPostId, String error, Instant publishedAt) {
        PublishLog log = new PublishLog();
        log.setScheduledPostId(scheduledPostId);
        log.setProvider(provider);
        log.setStatus(status);
        log.setExternalPostId(externalPostId);
        log.setErrorMessage(error);
        log.setPublishedAt(publishedAt);
        logs.save(log);
    }

    public record Claim(UUID scheduledPostId, UUID claimToken) { }
    private record ExpiredClaim(UUID id, UUID postId, String platform) { }
}
