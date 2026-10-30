package com.postiva.scheduler.service;

import java.util.Locale;

/**
 * Turns what Meta or Postiva's own checks said about a failed post into a sentence a shop owner can act on.
 * The raw text (tokens, containers, scopes, HTTP codes) means nothing to them; it goes to the server log instead.
 */
public final class PublishErrorMessages {
    private PublishErrorMessages() {
    }

    public static String forUser(String platform, String raw) {
        String text = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        if (containsAny(text, "hết hạn", "expired", "invalid oauth", "error validating access token",
                "session has been invalidated", "chưa có access token", "không chấp nhận token")) {
            return "Kết nối " + platform + " đã hết hạn. Vào Kết nối kênh để kết nối lại rồi đăng lại bài.";
        }
        if (containsAny(text, "permission", "quyền", "not authorized", "(#10)", "(#200)")) {
            return platform + " chưa cho Postiva đăng lên tài khoản này. Vào Kết nối kênh để kết nối lại rồi đăng lại bài.";
        }
        if (containsAny(text, "too many", "rate limit", "request limit", "giới hạn")) {
            return platform + " đang tạm giới hạn số bài đăng. Bạn thử lại sau ít phút.";
        }
        if (containsAny(text, "media", "container", "photo", "image", "video", "ảnh")) {
            return platform + " chưa nhận được ảnh hoặc video của bài. Bạn thử đăng lại sau ít phút.";
        }
        if (containsAny(text, "không còn tồn tại")) {
            return "Bài viết này đã bị xóa nên không đăng được.";
        }
        return platform + " chưa đăng được bài này. Bạn thử lại sau ít phút.";
    }

    public static String outcomeUnknown(String platform) {
        return "Chưa rõ bài đã lên " + platform + " hay chưa. Bạn mở " + platform
                + " kiểm tra: thấy bài thì bấm Đã đăng, không thấy thì bấm Chưa đăng để thử lại.";
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) return true;
        }
        return false;
    }
}
