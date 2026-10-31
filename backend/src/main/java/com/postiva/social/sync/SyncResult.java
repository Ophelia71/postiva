package com.postiva.social.sync;

/**
 * Outcome of reading a channel's posts back from the platform.
 * {@code warning} explains why nothing or no numbers could be read (usually a missing token permission).
 */
public record SyncResult(int imported, int updated, int removed, boolean metricsAvailable, String warning) {
}
