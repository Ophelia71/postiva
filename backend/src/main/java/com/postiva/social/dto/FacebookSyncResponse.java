package com.postiva.social.dto;

public record FacebookSyncResponse(
        String pageId,
        int syncedPosts,
        int removedPosts
) {
}
