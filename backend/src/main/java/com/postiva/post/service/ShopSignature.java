package com.postiva.post.service;

import com.postiva.ai.dto.AiPostResult;
import com.postiva.common.Platform;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Adds the shop's saved signature and brand hashtags to an AI draft. Done in code rather than by the AI so phone
 * numbers and addresses are copied exactly. The shop can still edit or remove them before posting.
 */
final class ShopSignature {
    static final int THREADS_MAX_LENGTH = 500;
    static final int MAX_HASHTAGS = 20;

    private ShopSignature() {
    }

    static AiPostResult apply(AiPostResult result, String signature, String brandHashtags) {
        String trimmedSignature = signature == null ? "" : signature.strip();
        Map<String, Object> contents = new LinkedHashMap<>();
        if (result.platformContents() != null) {
            result.platformContents().forEach((platform, value) ->
                    contents.put(platform, withSignature(platform, value, trimmedSignature)));
        }
        return new AiPostResult(result.title(), contents, mergeHashtags(brandHashtags, result.hashtags()),
                result.cta(), result.imageSuggestion());
    }

    private static Object withSignature(String platform, Object value, String signature) {
        if (signature.isEmpty() || !(value instanceof Map<?, ?> fields)) {
            return value;
        }
        String field = Platform.INSTAGRAM.name().equals(platform) ? "caption" : "content";
        Object text = fields.get(field);
        if (!(text instanceof String body) || body.contains(signature)) {
            return value;
        }
        String signed = body.stripTrailing() + "\n\n" + signature;
        // Threads rejects longer posts; the draft is more useful than the signature there.
        if (Platform.THREADS.name().equals(platform) && signed.length() > THREADS_MAX_LENGTH) {
            return value;
        }
        Map<String, Object> updated = new LinkedHashMap<>();
        fields.forEach((key, fieldValue) -> updated.put(String.valueOf(key), fieldValue));
        updated.put(field, signed);
        return updated;
    }

    /** Brand hashtags first, then the AI's, without duplicates (case-insensitive). */
    static List<String> mergeHashtags(String brandHashtags, List<String> generated) {
        Set<String> seen = new LinkedHashSet<>();
        Map<String, String> ordered = new LinkedHashMap<>();
        if (brandHashtags != null) {
            for (String raw : brandHashtags.split("[\\s,]+")) {
                add(ordered, seen, raw);
            }
        }
        if (generated != null) {
            for (String raw : generated) {
                add(ordered, seen, raw);
            }
        }
        return ordered.values().stream().limit(MAX_HASHTAGS).toList();
    }

    private static void add(Map<String, String> ordered, Set<String> seen, String raw) {
        if (raw == null) return;
        String tag = raw.strip().replaceAll("\\s+", "");
        if (tag.isEmpty() || tag.equals("#")) return;
        if (!tag.startsWith("#")) tag = "#" + tag;
        if (seen.add(tag.toLowerCase())) ordered.put(tag.toLowerCase(), tag);
    }
}
