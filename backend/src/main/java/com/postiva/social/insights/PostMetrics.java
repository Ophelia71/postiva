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
        Integer views,
        /** Public link to the post, when the platform returned one. */
        String permalink
) {
    public PostMetrics(String externalId, Platform platform, Instant publishedAt, String text, String mediaType,
                       Integer likes, Integer comments, Integer shares, Integer views) {
        this(externalId, platform, publishedAt, text, mediaType, likes, comments, shares, views, null);
    }

    public PostMetrics withInsights(Integer likes, Integer comments, Integer shares, Integer views) {
        return new PostMetrics(externalId, platform, publishedAt, text, mediaType, likes, comments, shares, views, permalink);
    }

    public boolean hasMetrics() {
        return likes != null || comments != null || shares != null || views != null;
    }
}
