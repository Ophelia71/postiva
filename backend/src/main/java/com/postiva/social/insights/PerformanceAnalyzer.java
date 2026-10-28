package com.postiva.social.insights;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns raw post metrics into findings. Pure and deterministic so it can be tested without Meta. */
@Component
public class PerformanceAnalyzer {
    /** Below this, patterns are noise; the summary says so instead of recommending anything. */
    static final int MIN_POSTS_FOR_PATTERNS = 4;

    private static final Pattern HASHTAG = Pattern.compile("#[\\p{L}\\p{N}_]+");
    private static final String[] WEEKDAY_LABELS = {"", "Thứ Hai", "Thứ Ba", "Thứ Tư", "Thứ Năm", "Thứ Sáu", "Thứ Bảy", "Chủ nhật"};
    private static final Map<String, String> MEDIA_LABELS = Map.of(
            "TEXT", "bài chỉ có chữ", "IMAGE", "bài có ảnh", "VIDEO", "bài video",
            "CAROUSEL", "bài nhiều ảnh", "LINK", "bài chia sẻ link");

    public PerformanceSummary analyze(List<PostMetrics> posts, String warning, ZoneId zone) {
        List<Scored> scored = posts.stream()
                .map(post -> new Scored(post, engagement(post), mediaCategory(post.mediaType())))
                .filter(item -> item.engagement() != null)
                .sorted(Comparator.comparingDouble(Scored::engagement).reversed())
                .toList();
        double postsPerWeek = postsPerWeek(posts);
        List<String> insights = new ArrayList<>();

        if (scored.size() < MIN_POSTS_FOR_PATTERNS) {
            insights.add(posts.isEmpty()
                    ? "Chưa có bài cũ để phân tích. Kế hoạch tuần này dựa trên thông tin sản phẩm và ngành hàng."
                    : "Mới có " + scored.size() + " bài đo được tương tác, chưa đủ để rút ra quy luật. Kế hoạch dựa chủ yếu vào thông tin sản phẩm.");
            return new PerformanceSummary(posts.size(), scored.size(), warning, average(scored),
                    highlights(scored, 3), List.of(), List.of(), null, Map.of(), null, null, List.of(),
                    round(postsPerWeek), insights);
        }

        double average = average(scored);
        Map<String, Double> byMedia = averageBy(scored, Scored::media);
        List<Integer> bestWeekdays = bestKeys(averageBy(scored, item -> weekday(item, zone)), minSamples(scored), scored,
                item -> weekday(item, zone)).stream().limit(3).toList();
        List<Integer> hours = bestKeys(averageBy(scored, item -> hour(item, zone)), minSamples(scored), scored,
                item -> hour(item, zone));
        Integer bestHour = hours.isEmpty() ? null : hours.getFirst();

        int topCount = Math.max(1, scored.size() / 3);
        List<Scored> top = scored.subList(0, topCount);
        List<Scored> rest = scored.subList(topCount, scored.size());
        Integer topLength = averageLength(top);
        Integer otherLength = averageLength(rest);
        List<String> hashtags = topHashtags(top);

        describeMedia(byMedia, insights);
        if (!bestWeekdays.isEmpty()) {
            insights.add("Ngày có tương tác cao nhất: " + String.join(", ",
                    bestWeekdays.stream().map(day -> WEEKDAY_LABELS[day]).toList()) + ".");
        }
        if (bestHour != null) {
            insights.add("Khung giờ hiệu quả nhất: " + bestHour + "h–" + (bestHour + 1) + "h.");
        }
        if (topLength != null && otherLength != null && otherLength > 0) {
            double ratio = (double) topLength / otherLength;
            if (ratio >= 1.3) insights.add("Bài hiệu quả thường dài hơn, khoảng " + topLength + " ký tự.");
            else if (ratio <= 0.75) insights.add("Bài hiệu quả thường ngắn gọn, khoảng " + topLength + " ký tự.");
        }
        if (!hashtags.isEmpty()) {
            insights.add("Hashtag hay có ở bài tốt: " + String.join(" ", hashtags) + ".");
        }
        if (postsPerWeek > 0 && postsPerWeek < 3) {
            insights.add("Shop đang đăng khoảng " + round(postsPerWeek) + " bài/tuần; đăng đều 4–7 bài/tuần giúp trang được thấy thường xuyên hơn.");
        }
        return new PerformanceSummary(posts.size(), scored.size(), warning, round(average), highlights(scored, 3),
                highlights(scored.reversed(), 2), bestWeekdays, bestHour, roundValues(byMedia), topLength, otherLength,
                hashtags, round(postsPerWeek), insights);
    }

    /** Comments and shares take more effort than a like, so they weigh more. Null when nothing was measured. */
    static Double engagement(PostMetrics post) {
        if (post.likes() == null && post.comments() == null && post.shares() == null) {
            return null;
        }
        return (double) (orZero(post.likes()) + 2 * orZero(post.comments()) + 3 * orZero(post.shares()));
    }

    static String mediaCategory(String mediaType) {
        String type = mediaType == null ? "" : mediaType.toUpperCase(Locale.ROOT);
        if (type.contains("CAROUSEL") || type.contains("ALBUM")) return "CAROUSEL";
        if (type.contains("VIDEO") || type.contains("REEL")) return "VIDEO";
        if (type.contains("PHOTO") || type.contains("IMAGE")) return "IMAGE";
        if (type.contains("LINK") || type.contains("SHARE")) return "LINK";
        return "TEXT";
    }

    private void describeMedia(Map<String, Double> byMedia, List<String> insights) {
        if (byMedia.size() < 2) return;
        var ranked = byMedia.entrySet().stream().sorted(Map.Entry.<String, Double>comparingByValue().reversed()).toList();
        var best = ranked.getFirst();
        var worst = ranked.getLast();
        if (worst.getValue() <= 0) {
            insights.add(capitalize(MEDIA_LABELS.get(best.getKey())) + " thu hút nhất.");
        } else if (best.getValue() / worst.getValue() >= 1.5) {
            insights.add(capitalize(MEDIA_LABELS.get(best.getKey())) + " đạt tương tác gấp "
                    + round(best.getValue() / worst.getValue()) + " lần " + MEDIA_LABELS.get(worst.getKey()) + ".");
        }
    }

    private int minSamples(List<Scored> scored) {
        return scored.size() >= 10 ? 2 : 1;
    }

    private <K> List<K> bestKeys(Map<K, Double> averages, int minSamples, List<Scored> scored,
                                 java.util.function.Function<Scored, K> key) {
        Map<K, Long> counts = new HashMap<>();
        for (Scored item : scored) counts.merge(key.apply(item), 1L, Long::sum);
        return averages.entrySet().stream()
                .filter(entry -> entry.getKey() != null && counts.getOrDefault(entry.getKey(), 0L) >= minSamples)
                .sorted(Map.Entry.<K, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList();
    }

    private <K> Map<K, Double> averageBy(List<Scored> scored, java.util.function.Function<Scored, K> key) {
        Map<K, double[]> sums = new LinkedHashMap<>();
        for (Scored item : scored) {
            K group = key.apply(item);
            if (group == null) continue;
            double[] sum = sums.computeIfAbsent(group, ignored -> new double[2]);
            sum[0] += item.engagement();
            sum[1] += 1;
        }
        Map<K, Double> averages = new LinkedHashMap<>();
        sums.forEach((group, sum) -> averages.put(group, sum[0] / sum[1]));
        return averages;
    }

    private Integer weekday(Scored item, ZoneId zone) {
        Instant at = item.post().publishedAt();
        return at == null ? null : ZonedDateTime.ofInstant(at, zone).getDayOfWeek().getValue();
    }

    private Integer hour(Scored item, ZoneId zone) {
        Instant at = item.post().publishedAt();
        return at == null ? null : ZonedDateTime.ofInstant(at, zone).getHour();
    }

    private Integer averageLength(List<Scored> items) {
        List<Integer> lengths = items.stream().map(item -> item.post().text())
                .filter(Objects::nonNull).map(String::length).filter(length -> length > 0).toList();
        return lengths.isEmpty() ? null : (int) Math.round(lengths.stream().mapToInt(Integer::intValue).average().orElse(0));
    }

    private List<String> topHashtags(List<Scored> top) {
        Map<String, Integer> counts = new HashMap<>();
        for (Scored item : top) {
            if (item.post().text() == null) continue;
            Matcher matcher = HASHTAG.matcher(item.post().text());
            while (matcher.find()) counts.merge(matcher.group().toLowerCase(Locale.ROOT), 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(8).map(Map.Entry::getKey).toList();
    }

    private double postsPerWeek(List<PostMetrics> posts) {
        List<Instant> dates = posts.stream().map(PostMetrics::publishedAt).filter(Objects::nonNull).sorted().toList();
        if (dates.size() < 2) return dates.size();
        double weeks = Math.max(1.0, Duration.between(dates.getFirst(), dates.getLast()).toDays() / 7.0);
        return dates.size() / weeks;
    }

    private List<PerformanceSummary.PostHighlight> highlights(List<Scored> items, int limit) {
        return items.stream().limit(limit).map(item -> new PerformanceSummary.PostHighlight(
                excerpt(item.post().text()), item.post().publishedAt(), item.media(), item.engagement(),
                item.post().likes(), item.post().comments(), item.post().shares(), item.post().views())).toList();
    }

    private static String excerpt(String text) {
        if (text == null || text.isBlank()) return "";
        String singleLine = text.replaceAll("\\s+", " ").trim();
        return singleLine.length() <= 160 ? singleLine : singleLine.substring(0, 157) + "...";
    }

    private static double average(List<Scored> scored) {
        return scored.stream().mapToDouble(Scored::engagement).average().orElse(0);
    }

    private static Map<String, Double> roundValues(Map<String, Double> values) {
        Map<String, Double> rounded = new LinkedHashMap<>();
        values.forEach((key, value) -> rounded.put(key, round(value)));
        return rounded;
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static String capitalize(String value) {
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private record Scored(PostMetrics post, Double engagement, String media) {
    }
}
