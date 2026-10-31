package com.postiva.post.service;

import com.postiva.ai.dto.AiPostResult;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ShopSignatureTest {
    private static final String SIGNATURE = "📍 12 Lê Lợi, Q1 · ☎ 0901 234 567";

    @Test
    void appendsSignatureToEveryPlatformThatFits() {
        AiPostResult result = ShopSignature.apply(draft("Bài Facebook", "Caption IG", "Bài Threads"), SIGNATURE, null);

        assertEquals("Bài Facebook\n\n" + SIGNATURE, field(result, "FACEBOOK", "content"));
        assertEquals("Caption IG\n\n" + SIGNATURE, field(result, "INSTAGRAM", "caption"));
        assertEquals("Bài Threads\n\n" + SIGNATURE, field(result, "THREADS", "content"));
        assertEquals("Tiêu đề", field(result, "FACEBOOK", "title"));
    }

    @Test
    void keepsThreadsUnderItsLimitAndNeverSignsTwice() {
        String longThreads = "x".repeat(480);
        String alreadySigned = "Bài có sẵn chữ ký\n\n" + SIGNATURE;

        AiPostResult result = ShopSignature.apply(draft(alreadySigned, "Caption", longThreads), SIGNATURE, null);

        assertEquals(alreadySigned, field(result, "FACEBOOK", "content"));
        assertEquals(longThreads, field(result, "THREADS", "content"));
    }

    @Test
    void blankSignatureLeavesDraftUntouched() {
        AiPostResult original = draft("A", "B", "C");

        AiPostResult result = ShopSignature.apply(original, "  ", null);

        assertSame(original.platformContents().get("FACEBOOK"), result.platformContents().get("FACEBOOK"));
    }

    @Test
    void brandHashtagsComeFirstWithoutDuplicates() {
        assertEquals(List.of("#CaPheMuoi", "#postiva", "#cafe"),
                ShopSignature.mergeHashtags("CaPheMuoi, #postiva", List.of("#capheMUOI", "cafe", "#postiva")));
    }

    @Test
    void hashtagsAreCappedAtTwenty() {
        List<String> generated = java.util.stream.IntStream.range(0, 30).mapToObj(i -> "#tag" + i).toList();

        assertEquals(20, ShopSignature.mergeHashtags("#brand", generated).size());
    }

    private static AiPostResult draft(String facebook, String instagram, String threads) {
        Map<String, Object> contents = new LinkedHashMap<>();
        contents.put("FACEBOOK", Map.of("title", "Tiêu đề", "content", facebook));
        contents.put("INSTAGRAM", Map.of("title", "Tiêu đề", "caption", instagram));
        contents.put("THREADS", Map.of("title", "Tiêu đề", "content", threads));
        return new AiPostResult("Tiêu đề", contents, List.of("#cafe"), "Nhắn shop", "Ảnh ly cà phê");
    }

    private static Object field(AiPostResult result, String platform, String name) {
        return ((Map<?, ?>) result.platformContents().get(platform)).get(name);
    }
}
