package com.postiva.social.publishing;

import com.postiva.common.Platform;
import com.postiva.post.entity.GeneratedPost;

import java.util.Map;

final class PlatformPostContent {
    private PlatformPostContent() {
    }

    static String require(GeneratedPost post, Platform platform) {
        Map<?, ?> values = values(post, platform);
        Object preferred = values.get(platform == Platform.INSTAGRAM ? "caption" : "content");
        Object fallback = values.get(platform == Platform.INSTAGRAM ? "content" : "caption");
        String content = text(preferred);
        if (content.isBlank()) {
            content = text(fallback);
        }
        if (content.isBlank()) {
            throw new IllegalStateException("Bài viết chưa có nội dung " + label(platform));
        }
        return content;
    }

    static String mediaType(GeneratedPost post, Platform platform) {
        return text(values(post, platform).get("mediaType")).toUpperCase();
    }

    private static Map<?, ?> values(GeneratedPost post, Platform platform) {
        if (post.getPlatformContents() == null) {
            return Map.of();
        }
        Object value = post.getPlatformContents().get(platform.name());
        if (!(value instanceof Map<?, ?>)) {
            value = post.getPlatformContents().get(platform.name().toLowerCase());
        }
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static String label(Platform platform) {
        return switch (platform) {
            case FACEBOOK -> "Facebook";
            case INSTAGRAM -> "Instagram";
            case THREADS -> "Threads";
        };
    }
}
