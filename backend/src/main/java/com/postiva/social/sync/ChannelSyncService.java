package com.postiva.social.sync;

import com.postiva.auth.service.CurrentUserService;
import com.postiva.common.Platform;
import com.postiva.common.PostStatus;
import com.postiva.post.entity.GeneratedPost;
import com.postiva.post.entity.ProductBrief;
import com.postiva.post.repository.GeneratedPostRepository;
import com.postiva.post.repository.ProductBriefRepository;
import com.postiva.scheduler.ScheduledPostStatus;
import com.postiva.scheduler.entity.PublishLog;
import com.postiva.scheduler.entity.ScheduledPost;
import com.postiva.scheduler.repository.PublishLogRepository;
import com.postiva.scheduler.repository.ScheduledPostRepository;
import com.postiva.social.entity.SocialAccount;
import com.postiva.social.insights.PostInsightsService;
import com.postiva.social.insights.PostMetrics;
import com.postiva.social.service.SocialAccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;

/**
 * Reads a channel's posts back from Facebook, Instagram or Threads: imports posts Postiva does not know yet,
 * refreshes likes/comments/shares/views of every known post, and drops local copies of posts deleted on the
 * platform. Runs after connecting a channel, whenever the shop opens Postiva (at most every
 * {@link #REFRESH_ON_OPEN}), on a timer for channels nobody looked at for {@link #REFRESH_IN_BACKGROUND},
 * and from the "Cập nhật" button.
 */
@Service
public class ChannelSyncService {
    static final int FACEBOOK_LIMIT = 100;
    static final int OTHER_LIMIT = 50;
    /** Threads needs one call per post for numbers; the newest ones matter most. */
    static final int THREADS_MEASURED = 25;
    /** A post published moments ago may not be listed by the platform yet; never treat it as deleted. */
    static final Duration REMOVAL_GRACE = Duration.ofMinutes(15);
    /** Opening Postiva again within this time shows the numbers read last time; Meta's rate limits stay far away. */
    static final Duration REFRESH_ON_OPEN = Duration.ofMinutes(30);
    static final Duration REFRESH_IN_BACKGROUND = Duration.ofHours(6);
    /** Channels refreshed per timer run, so a growing user base never floods Meta or the sync threads. */
    static final int BACKGROUND_BATCH = 20;

    private static final Logger log = LoggerFactory.getLogger(ChannelSyncService.class);

    private final SocialAccountService accounts;
    private final PostInsightsService insights;
    private final ProductBriefRepository briefs;
    private final GeneratedPostRepository posts;
    private final ScheduledPostRepository schedules;
    private final PublishLogRepository logs;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Executor executor;
    private final CurrentUserService currentUser;
    /** Channels being synced right now; a second request for the same channel is dropped, not queued. */
    private final Set<UUID> running = ConcurrentHashMap.newKeySet();

    public ChannelSyncService(SocialAccountService accounts, PostInsightsService insights, ProductBriefRepository briefs,
                              GeneratedPostRepository posts, ScheduledPostRepository schedules, PublishLogRepository logs,
                              JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
                              @Qualifier("channelSyncExecutor") Executor executor, CurrentUserService currentUser) {
        this.accounts = accounts;
        this.insights = insights;
        this.briefs = briefs;
        this.posts = posts;
        this.schedules = schedules;
        this.logs = logs;
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
        this.executor = executor;
        this.currentUser = currentUser;
    }

    /** Sync one of the signed-in user's channels. */
    public SyncResult syncCurrentUserAccount(UUID accountId) {
        return sync(accounts.requireCurrentUserAccount(accountId).getId());
    }

    /** Best effort, right after connecting: a failure only means the shop presses "Cập nhật" later. */
    public void syncInBackground(UUID accountId) {
        if (!running.add(accountId)) return;
        try {
            executor.execute(() -> {
                try {
                    SyncResult result = sync(accountId);
                    log.info("Synced social account {}: {} imported, {} updated", accountId, result.imported(), result.updated());
                } catch (RuntimeException exception) {
                    log.warn("Background sync of social account {} failed: {}", accountId, exception.getMessage());
                } finally {
                    running.remove(accountId);
                }
            });
        } catch (RejectedExecutionException exception) {
            running.remove(accountId);
            log.warn("Background sync of social account {} skipped: executor busy", accountId);
        }
    }

    /**
     * Called when the shop opens Postiva: refreshes their channels not read in the last {@link #REFRESH_ON_OPEN}.
     * Returns how many started, so the page knows whether newer numbers are on the way.
     */
    public int refreshCurrentUserChannelsOnOpen() {
        List<UUID> due = claimDue(REFRESH_ON_OPEN, currentUser.requireCurrentUserId(), Integer.MAX_VALUE);
        due.forEach(this::syncInBackground);
        return due.size();
    }

    /** Keeps numbers fresh for shops that have not opened Postiva for a while. */
    @Scheduled(fixedDelay = 1_800_000, initialDelay = 600_000)
    public void refreshQuietChannels() {
        claimDue(REFRESH_IN_BACKGROUND, null, BACKGROUND_BATCH).forEach(this::syncInBackground);
    }

    /**
     * Marks channels due for a refresh as refreshed now and returns them. Doing both in one statement means two
     * tabs, the timer and the open-page call never start the same channel twice.
     */
    private List<UUID> claimDue(Duration olderThan, UUID userId, int limit) {
        Object[] args = userId == null
                ? new Object[]{olderThan.toSeconds(), limit}
                : new Object[]{olderThan.toSeconds(), userId, limit};
        String sql = """
                UPDATE social_accounts SET last_synced_at = now()
                WHERE id IN (
                    SELECT a.id FROM social_accounts a JOIN users u ON u.id = a.user_id
                    WHERE a.disconnected_at IS NULL AND a.access_token_encrypted IS NOT NULL
                      AND u.status = 'ACTIVE'
                      AND (a.last_synced_at IS NULL OR a.last_synced_at < now() - make_interval(secs => ?))
                      %s
                    ORDER BY a.last_synced_at NULLS FIRST
                    LIMIT ?
                    FOR UPDATE OF a SKIP LOCKED)
                RETURNING id""".formatted(userId == null ? "" : "AND a.user_id = ?");
        return jdbc.queryForList(sql, UUID.class, args);
    }

    public SyncResult sync(UUID accountId) {
        jdbc.update("UPDATE social_accounts SET last_synced_at = now() WHERE id = ?", accountId);
        // Token refresh and Meta calls happen outside any database transaction.
        SocialAccount account = accounts.prepareForPublishing(accountId);
        int limit = account.getProvider() == Platform.FACEBOOK ? FACEBOOK_LIMIT : OTHER_LIMIT;
        var recent = insights.recentPosts(account, accounts.decryptToken(account), limit, THREADS_MEASURED);
        if (recent.posts().isEmpty()) {
            // Nothing listed can mean an API error as much as an empty channel: never delete on that basis.
            return new SyncResult(0, 0, 0, false, recent.warning());
        }
        SyncResult result = transactions.execute(status -> apply(account, recent.posts(), limit));
        String warning = recent.warning() != null ? recent.warning()
                : result.metricsAvailable() ? null : "Đã lấy bài nhưng chưa đọc được lượt thích, bình luận. Bạn thử kết nối lại kênh.";
        return new SyncResult(result.imported(), result.updated(), result.removed(), result.metricsAvailable(), warning);
    }

    private SyncResult apply(SocialAccount account, List<PostMetrics> live, int limit) {
        int imported = 0;
        int updated = 0;
        for (PostMetrics post : live) {
            if (post.externalId() == null || post.externalId().isBlank()) continue;
            Optional<ScheduledPost> known = knownSchedule(account, post.externalId());
            if (known.isPresent()) {
                refreshImportedTime(known.get(), post);
                saveMetrics(known.get().getId(), post);
                updated++;
            } else {
                ScheduledPost scheduled = importPost(account, post);
                saveMetrics(scheduled.getId(), post);
                imported++;
            }
        }
        int removed = removeDeleted(account, live, live.size() >= limit);
        boolean metricsAvailable = live.stream().anyMatch(PostMetrics::hasMetrics);
        return new SyncResult(imported, updated, removed, metricsAvailable, null);
    }

    private Optional<ScheduledPost> knownSchedule(SocialAccount account, String externalId) {
        return logs.findFirstByProviderAndExternalPostId(account.getProvider().name(), externalId)
                .map(PublishLog::getScheduledPostId)
                .flatMap(schedules::findById)
                .filter(scheduled -> account.getId().equals(scheduled.getSocialAccountId()));
    }

    /**
     * Drops local posts that the platform no longer lists. Only posts inside the window the platform just
     * returned are considered: older posts are simply beyond the page that was read, not deleted.
     */
    private int removeDeleted(SocialAccount account, List<PostMetrics> live, boolean pageWasFull) {
        Set<String> liveIds = live.stream().map(PostMetrics::externalId).filter(Objects::nonNull).collect(Collectors.toSet());
        Instant oldestListed = live.stream().map(PostMetrics::publishedAt).filter(Objects::nonNull).min(Instant::compareTo).orElse(null);
        if (oldestListed == null) return 0;
        Instant newestRemovable = Instant.now().minus(REMOVAL_GRACE);
        int removed = 0;
        for (ScheduledPost scheduled : schedules.findBySocialAccountIdAndStatus(account.getId(), ScheduledPostStatus.PUBLISHED)) {
            Instant publishedAt = scheduled.getPublishedAt();
            if (publishedAt == null || publishedAt.isAfter(newestRemovable)) continue;
            // Anything older than the oldest listed post was simply not read. With a full page the edge itself is
            // uncertain too (posts sharing that time may sit on the next page), so it is kept as well.
            if (publishedAt.isBefore(oldestListed) || (pageWasFull && !publishedAt.isAfter(oldestListed))) continue;
            String externalId = logs.findFirstByScheduledPostId(scheduled.getId()).map(PublishLog::getExternalPostId).orElse(null);
            if (externalId == null || externalId.isBlank() || liveIds.contains(externalId)) continue;
            deleteLocalCopy(scheduled);
            removed++;
        }
        return removed;
    }

    private void deleteLocalCopy(ScheduledPost scheduled) {
        UUID postId = scheduled.getPostId();
        logs.deleteByScheduledPostId(scheduled.getId());
        schedules.delete(scheduled);
        // The post itself goes only when no other channel still uses it.
        if (postId != null && schedules.findByPostId(postId).stream().allMatch(other -> other.getId().equals(scheduled.getId()))) {
            posts.findById(postId).ifPresent(posts::delete);
        }
    }

    private void refreshImportedTime(ScheduledPost scheduled, PostMetrics source) {
        if (source.publishedAt() == null || scheduled.getPostId() == null || !isImported(scheduled.getPostId(), scheduled.getPlatform())) return;
        scheduled.setScheduledTime(source.publishedAt());
        scheduled.setPublishedAt(source.publishedAt());
        schedules.save(scheduled);
    }

    private boolean isImported(UUID postId, Platform platform) {
        return posts.findById(postId)
                .flatMap(post -> post.getBriefId() == null ? Optional.empty() : briefs.findById(post.getBriefId()))
                .map(brief -> importCode(platform).equals(brief.getIndustryCode()))
                .orElse(false);
    }

    static String importCode(Platform platform) {
        return platform.name() + "_IMPORT";
    }

    private ScheduledPost importPost(SocialAccount account, PostMetrics source) {
        Platform platform = account.getProvider();
        ProductBrief brief = new ProductBrief();
        brief.setUserId(account.getUserId());
        brief.setProductName("Bài nhập từ " + label(platform));
        brief.setIndustryCode(importCode(platform));
        brief.setTargetAudience("Người theo dõi " + label(platform));
        brief.setGoal("Nhập lịch sử bài đăng");
        brief.setTone("Nhập từ " + label(platform));
        brief.setPlatforms(List.of(platform));
        brief = briefs.save(brief);

        Map<String, Object> content = new HashMap<>();
        content.put(platform == Platform.INSTAGRAM ? "caption" : "content", source.text() == null ? "" : source.text());
        content.put("platformPostId", source.externalId());
        content.put("permalinkUrl", source.permalink() == null ? "" : source.permalink());
        content.put("mediaType", source.mediaType() == null ? "" : source.mediaType());
        Map<String, Object> contents = new HashMap<>();
        contents.put(platform.name(), content);

        GeneratedPost post = new GeneratedPost();
        post.setBriefId(brief.getId());
        post.setUserId(account.getUserId());
        post.setPlatformContents(contents);
        post.setStatus(PostStatus.PUBLISHED);
        post = posts.save(post);

        Instant publishedAt = source.publishedAt() == null ? Instant.now() : source.publishedAt();
        ScheduledPost scheduled = new ScheduledPost();
        scheduled.setPostId(post.getId());
        scheduled.setSocialAccountId(account.getId());
        scheduled.setPlatform(platform);
        scheduled.setScheduledTime(publishedAt);
        scheduled.setStatus(ScheduledPostStatus.PUBLISHED);
        scheduled.setAttemptCount(1);
        scheduled.setPublishedAt(publishedAt);
        // Flushed now: the engagement row written next with JDBC references it.
        scheduled = schedules.saveAndFlush(scheduled);

        PublishLog entry = new PublishLog();
        entry.setScheduledPostId(scheduled.getId());
        entry.setProvider(platform.name());
        entry.setExternalPostId(source.externalId());
        entry.setStatus("SUCCESS");
        entry.setPublishedAt(publishedAt);
        logs.save(entry);
        return scheduled;
    }

    private void saveMetrics(UUID scheduledPostId, PostMetrics source) {
        if (!source.hasMetrics()) return;
        jdbc.update("""
                INSERT INTO post_metrics (scheduled_post_id, likes, comments, shares, views, fetched_at)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (scheduled_post_id) DO UPDATE SET likes = EXCLUDED.likes, comments = EXCLUDED.comments,
                    shares = EXCLUDED.shares, views = EXCLUDED.views, fetched_at = EXCLUDED.fetched_at
                """, scheduledPostId, nonNegative(source.likes()), nonNegative(source.comments()),
                nonNegative(source.shares()), nonNegative(source.views()));
    }

    private static Integer nonNegative(Integer value) {
        return value == null ? null : Math.max(0, value);
    }

    private static String label(Platform platform) {
        return switch (platform) {
            case FACEBOOK -> "Facebook";
            case INSTAGRAM -> "Instagram";
            case THREADS -> "Threads";
        };
    }
}
