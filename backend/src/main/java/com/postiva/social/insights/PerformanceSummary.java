package com.postiva.social.insights;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * What a shop's recent posts say about what works. Every field is optional in spirit: with few or unmeasured posts
 * most lists are empty and {@link #insights()} explains that instead of guessing.
 */
public record PerformanceSummary(
        int postsAnalyzed,
        int postsWithMetrics,
        String warning,
        double averageEngagement,
        List<PostHighlight> topPosts,
        List<PostHighlight> weakPosts,
        /** ISO weekdays (1 = Monday) with the highest average engagement, best first. */
        List<Integer> bestWeekdays,
        /** Local hour (0-23) with the highest average engagement, or null when there is not enough data. */
        Integer bestHour,
        Map<String, Double> engagementByMediaType,
        Integer topPostAverageLength,
        Integer otherPostAverageLength,
        List<String> topHashtags,
        double postsPerWeek,
        /** Plain-language findings, shown to the shop and given to the AI planner. */
        List<String> insights
) {
    public record PostHighlight(String excerpt, Instant publishedAt, String mediaType, double engagement,
                                Integer likes, Integer comments, Integer shares, Integer views) {
    }
}
