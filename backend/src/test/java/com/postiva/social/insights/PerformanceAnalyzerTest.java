package com.postiva.social.insights;

import com.postiva.common.Platform;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerformanceAnalyzerTest {
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private final PerformanceAnalyzer analyzer = new PerformanceAnalyzer();

    @Test
    void findsBestMediaDayHourAndHashtags() {
        List<PostMetrics> posts = new ArrayList<>();
        // Friday 20:00 videos do far better than Monday 9:00 text posts.
        for (int week = 0; week < 4; week++) {
            posts.add(post(at(2026, 10, 2 + week * 7, 20), "VIDEO", "Video pha chế #caphe #postiva " + "x".repeat(200), 40, 6, 3));
            posts.add(post(at(2026, 9, 28 + week * 7, 9), "TEXT", "Thông báo ngắn", 5, 0, 0));
        }

        PerformanceSummary summary = analyzer.analyze(posts, null, VN);

        assertEquals(8, summary.postsWithMetrics());
        assertEquals(5, summary.bestWeekdays().getFirst());
        assertEquals(20, summary.bestHour());
        assertEquals(List.of("#caphe", "#postiva"), summary.topHashtags());
        assertEquals(61.0, summary.topPosts().getFirst().engagement());
        assertTrue(summary.insights().stream().anyMatch(line -> line.startsWith("Bài video đạt tương tác gấp")));
        assertTrue(summary.insights().stream().anyMatch(line -> line.contains("Thứ Sáu")));
        assertTrue(summary.insights().stream().anyMatch(line -> line.contains("20h–21h")));
        assertTrue(summary.insights().stream().anyMatch(line -> line.contains("dài hơn")));
    }

    @Test
    void tooFewMeasuredPostsGivesNoPatterns() {
        List<PostMetrics> posts = List.of(
                post(at(2026, 10, 2, 20), "IMAGE", "Ảnh", 10, 1, 0),
                // Threads post without insights permission: counted as analysed, not as measured.
                new PostMetrics("t1", Platform.THREADS, at(2026, 10, 3, 20), "Chữ", "TEXT_POST", null, null, null, null));

        PerformanceSummary summary = analyzer.analyze(posts, "thiếu quyền", VN);

        assertEquals(2, summary.postsAnalyzed());
        assertEquals(1, summary.postsWithMetrics());
        assertEquals("thiếu quyền", summary.warning());
        assertNull(summary.bestHour());
        assertTrue(summary.bestWeekdays().isEmpty());
        assertTrue(summary.insights().getFirst().startsWith("Mới có 1 bài đo được tương tác"));
    }

    @Test
    void noPostsExplainsPlanUsesProductInfo() {
        PerformanceSummary summary = analyzer.analyze(List.of(), null, VN);

        assertEquals(0, summary.postsAnalyzed());
        assertTrue(summary.insights().getFirst().startsWith("Chưa có bài cũ"));
    }

    @Test
    void weighsCommentsAndSharesAboveLikes() {
        assertEquals(10.0, PerformanceAnalyzer.engagement(post(Instant.now(), "TEXT", "", 1, 3, 1)));
        assertNull(PerformanceAnalyzer.engagement(new PostMetrics("x", Platform.THREADS, null, "", "", null, null, null, 5)));
    }

    @Test
    void normalisesMediaTypesAcrossPlatforms() {
        assertEquals("CAROUSEL", PerformanceAnalyzer.mediaCategory("CAROUSEL_ALBUM"));
        assertEquals("CAROUSEL", PerformanceAnalyzer.mediaCategory("ALBUM"));
        assertEquals("VIDEO", PerformanceAnalyzer.mediaCategory("REELS"));
        assertEquals("IMAGE", PerformanceAnalyzer.mediaCategory("PHOTO"));
        assertEquals("LINK", PerformanceAnalyzer.mediaCategory("SHARE"));
        assertEquals("TEXT", PerformanceAnalyzer.mediaCategory("TEXT_POST"));
    }

    private static Instant at(int year, int month, int day, int hour) {
        return ZonedDateTime.of(year, month, 1, hour, 0, 0, 0, VN).plusDays(day - 1).toInstant();
    }

    private static PostMetrics post(Instant at, String media, String text, int likes, int comments, int shares) {
        return new PostMetrics(at.toString() + media, Platform.FACEBOOK, at, text, media, likes, comments, shares, null);
    }
}
