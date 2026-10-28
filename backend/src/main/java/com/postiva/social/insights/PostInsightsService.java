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
        try {
            return switch (account.getProvider()) {
                case FACEBOOK -> new RecentPosts(
                        facebookClient.getPagePostMetrics(account.getPageId(), accessToken, POST_LIMIT), null);
                case INSTAGRAM -> new RecentPosts(instagramClient.getRecentMediaMetrics(account.getPageId(), accessToken,
                        InstagramGraphClient.ApiMode.fromAccountMetadata(account.getAccountMetadata()), POST_LIMIT), null);
                case THREADS -> threadsPosts(accessToken);
            };
        } catch (RuntimeException exception) {
            log.warn("Could not read recent posts for social account {}: {}", account.getId(), exception.getMessage());
            return new RecentPosts(List.of(), switch (account.getProvider()) {
                case FACEBOOK -> "Không đọc được tương tác bài cũ trên Facebook. Token cần quyền pages_read_engagement.";
                case INSTAGRAM -> "Không đọc được bài cũ trên Instagram. Hãy kiểm tra lại token của tài khoản.";
                case THREADS -> "Không đọc được bài cũ trên Threads. Hãy kiểm tra lại token của tài khoản.";
            });
        }
    }

    private RecentPosts threadsPosts(String accessToken) {
        List<PostMetrics> posts = threadsClient.getRecentThreads(accessToken, POST_LIMIT);
        List<PostMetrics> measured = new ArrayList<>();
        for (PostMetrics post : posts.subList(0, Math.min(THREADS_INSIGHTS_LIMIT, posts.size()))) {
            Map<String, Integer> metrics;
            try {
                metrics = threadsClient.getThreadInsights(post.externalId(), accessToken);
            } catch (RuntimeException exception) {
                // The first failure is almost always a missing scope; every other post would fail the same way.
                log.warn("Threads insights unavailable: {}", exception.getMessage());
                return new RecentPosts(posts,
                        "Chưa đọc được lượt xem/thích trên Threads. Token cần quyền threads_manage_insights.");
            }
            Integer shares = metrics.containsKey("reposts") || metrics.containsKey("quotes")
                    ? metrics.getOrDefault("reposts", 0) + metrics.getOrDefault("quotes", 0) : null;
            measured.add(post.withInsights(metrics.get("likes"), metrics.get("replies"), shares, metrics.get("views")));
        }
        return new RecentPosts(measured, null);
    }

    /** {@code warning} is set when the posts could not be read at all or came back without engagement numbers. */
    public record RecentPosts(List<PostMetrics> posts, String warning) {
    }
}
