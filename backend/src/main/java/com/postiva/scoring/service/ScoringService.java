package com.postiva.scoring.service;

import com.postiva.post.entity.GeneratedPost;
import com.postiva.post.service.PostService;
import com.postiva.scoring.dto.PostScoreResponse;
import com.postiva.scoring.entity.PostScore;
import com.postiva.scoring.repository.PostScoreRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ScoringService {
    private final PostService postService;
    private final PostScoreRepository scoreRepository;

    public ScoringService(PostService postService, PostScoreRepository scoreRepository) {
        this.postService = postService;
        this.scoreRepository = scoreRepository;
    }

    public PostScoreResponse score(UUID postId) {
        GeneratedPost post = postService.getPost(postId);
        String content = post.getPlatformContents().toString().toLowerCase();
        List<String> strengths = new ArrayList<>();
        List<String> weaknesses = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        int score = 0;

        score += addRule(!content.isBlank(), 15, "Có nội dung bài viết", "Bài viết chưa có nội dung", strengths, weaknesses);
        score += addRule(post.getCta() != null && !post.getCta().isBlank(), 20, "Có CTA", "Thiếu lời kêu gọi hành động", strengths, weaknesses);
        score += addRule(!post.getHashtags().isEmpty(), 10, "Có hashtag", "Thiếu hashtag cho Instagram", strengths, weaknesses);
        score += addRule(post.getImageSuggestion() != null && !post.getImageSuggestion().isBlank(), 10, "Có gợi ý hình ảnh", "Thiếu gợi ý hình ảnh", strengths, weaknesses);
        score += addRule(content.length() >= 80, 15, "Nội dung đủ chi tiết", "Nội dung còn quá ngắn", strengths, weaknesses);
        score += addRule(content.contains("giá") || content.matches(".*\\d+.*"), 15, "Có thông tin giá hoặc số liệu", "Nên bổ sung giá hoặc ưu đãi cụ thể", strengths, weaknesses);
        score += addRule(content.contains("nhắn") || content.contains("đặt") || content.contains("ghé"), 15, "Có hành động rõ", "CTA nên nói rõ khách cần làm gì tiếp theo", strengths, weaknesses);

        if (weaknesses.contains("Thiếu lời kêu gọi hành động")) {
            suggestions.add("Thêm câu như: Nhắn tin ngay để được tư vấn.");
        }
        if (weaknesses.contains("Nên bổ sung giá hoặc ưu đãi cụ thể")) {
            suggestions.add("Bổ sung giá, ưu đãi hoặc thời gian khuyến mãi để bài viết dễ ra quyết định hơn.");
        }
        if (suggestions.isEmpty()) {
            suggestions.add("Bài viết đã đủ thông tin cơ bản, có thể chỉnh giọng văn cho tự nhiên hơn trước khi đăng.");
        }

        PostScore entity = new PostScore();
        entity.setPostId(postId);
        entity.setScore(score);
        entity.setStrengths(strengths);
        entity.setWeaknesses(weaknesses);
        entity.setSuggestions(suggestions);
        scoreRepository.save(entity);

        return new PostScoreResponse(postId, score, strengths, weaknesses, suggestions);
    }

    private int addRule(boolean condition, int points, String strength, String weakness,
                        List<String> strengths, List<String> weaknesses) {
        if (condition) {
            strengths.add(strength);
            return points;
        }
        weaknesses.add(weakness);
        return 0;
    }
}
