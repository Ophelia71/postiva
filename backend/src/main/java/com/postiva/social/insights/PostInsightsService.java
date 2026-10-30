package com.postiva.social.insights;

import com.postiva.social.client.FacebookGraphClient;
import com.postiva.social.client.InstagramGraphClient;
import com.postiva.social.client.ThreadsGraphClient;
import com.postiva.social.entity.SocialAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads the engagement of an account's recent posts from Meta. Never throws for Meta-side problems: a missing
 * permission or an API error only means planning continues without data, with a warning the shop can act on.
 */
@Service
public class PostInsightsService {
    static final int POST_LIMIT = 25;
    /** Threads needs one insights call per post, so only the newest posts are measured. */
    static final int THREADS_INSIGHTS_LIMIT = 12;

    private static final Logger log = LoggerFactory.getLogger(PostInsightsService.class);

    private final FacebookGraphClient facebookClient;
    private final InstagramGraphClient instagramClient;
    private final ThreadsGraphClient threadsClient;

    public PostInsightsService(FacebookGraphClient facebookClient,
                               InstagramGraphClient instagramClient,
                               ThreadsGraphClient threadsClient) {
        this.facebookClient = facebookClient;
        this.instagramClient = instagramClient;
        this.threadsClient = threadsClient;
    }

    public RecentPosts recentPosts(SocialAccount account, String accessToken) {
        return recentPosts(account, accessToken, POST_LIMIT, THREADS_INSIGHTS_LIMIT);
    }

    /**
     * {@code limit} posts at most; on Threads only the newest {@code threadsMeasured} get engagement numbers, because
     * each one costs a separate call. Posts beyond that are still returned, without numbers.
     */
    public RecentPosts recentPosts(SocialAccount account, String accessToken, int limit, int threadsMeasured) {
        try {
            return switch (account.getProvider()) {
                case FACEBOOK -> new RecentPosts(
                        facebookClient.getPagePostMetrics(account.getPageId(), accessToken, limit), null);
                case INSTAGRAM -> new RecentPosts(instagramClient.getRecentMediaMetrics(account.getPageId(), accessToken,
                        InstagramGraphClient.ApiMode.fromAccountMetadata(account.getAccountMetadata()), limit), null);
                case THREADS -> threadsPosts(accessToken, limit, threadsMeasured);
            };
        } catch (RuntimeException exception) {
            log.warn("Could not read recent posts for social account {}: {}", account.getId(), exception.getMessage());
            return new RecentPosts(List.of(), switch (account.getProvider()) {
                case FACEBOOK -> "Chưa đọc được bài cũ trên Facebook. Bạn thử kết nối lại kênh ở trang Kết nối kênh.";
                case INSTAGRAM -> "Chưa đọc được bài cũ trên Instagram. Bạn thử kết nối lại kênh ở trang Kết nối kênh.";
                case THREADS -> "Chưa đọc được bài cũ trên Threads. Bạn thử kết nối lại kênh ở trang Kết nối kênh.";
            });
        }
    }

    private RecentPosts threadsPosts(String accessToken, int limit, int measuredLimit) {
        List<PostMetrics> posts = threadsClient.getRecentThreads(accessToken, limit);
        List<PostMetrics> measured = new ArrayList<>();
        int toMeasure = Math.min(measuredLimit, posts.size());
        for (PostMetrics post : posts.subList(0, toMeasure)) {
            Map<String, Integer> metrics;
            try {
                metrics = threadsClient.getThreadInsights(post.externalId(), accessToken);
            } catch (RuntimeException exception) {
                // The first failure is almost always a missing scope; every other post would fail the same way.
                log.warn("Threads insights unavailable: {}", exception.getMessage());
                return new RecentPosts(posts,
                        "Chưa đọc được lượt xem và lượt thích trên Threads. Bạn thử kết nối lại kênh ở trang Kết nối kênh.");
            }
            Integer shares = metrics.containsKey("reposts") || metrics.containsKey("quotes")
                    ? metrics.getOrDefault("reposts", 0) + metrics.getOrDefault("quotes", 0) : null;
            measured.add(post.withInsights(metrics.get("likes"), metrics.get("replies"), shares, metrics.get("views")));
        }
        measured.addAll(posts.subList(toMeasure, posts.size()));
        return new RecentPosts(measured, null);
    }

    /** {@code warning} is set when the posts could not be read at all or came back without engagement numbers. */
    public record RecentPosts(List<PostMetrics> posts, String warning) {
    }
}
