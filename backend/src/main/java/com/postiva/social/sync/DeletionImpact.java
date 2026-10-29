package com.postiva.social.sync;

/** What removing a channel will delete, shown to the shop before it confirms. */
public record DeletionImpact(int publishedPosts, int scheduledPosts, int failedPosts, int weeklyPlans) {
}
