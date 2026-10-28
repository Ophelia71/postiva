package com.postiva.social.insights;

import com.postiva.common.Platform;

import java.time.Instant;

/**
 * Engagement of one already-published post, as far as the account's token lets us read it.
 * A null count means Meta did not return that metric (missing permission or unsupported), not zero.
 */
public record PostMetrics(
        String externalId,
        Platform platform,
        Instant publishedAt,
        String text,
        String mediaType,
        Integer likes,
        Integer comments,
        Integer shares,
        Integer views
) {
    public PostMetrics withInsights(Integer likes, Integer comments, Integer shares, Integer views) {
        return new PostMetrics(externalId, platform, publishedAt, text, mediaType, likes, comments, shares, views);
    }
}
