package com.postiva.post.service;

import com.postiva.ai.dto.AiPostResult;
import com.postiva.ai.service.OpenAiService;
import com.postiva.auth.service.CurrentUserService;
import com.postiva.common.PostStatus;
import com.postiva.file.service.FileService;
import com.postiva.post.dto.GeneratePostRequest;
import com.postiva.post.dto.GeneratePostResponse;
import com.postiva.post.dto.UpdatePostRequest;
import com.postiva.post.entity.GeneratedPost;
import com.postiva.post.entity.ProductBrief;
import com.postiva.post.repository.GeneratedPostRepository;
import com.postiva.post.repository.ProductBriefRepository;
import com.postiva.template.entity.IndustryTemplate;
import com.postiva.template.service.TemplateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PostService {
    private static final String TITLE_KEY = "_title";

    private final ProductBriefRepository briefRepository;
    private final GeneratedPostRepository postRepository;
    private final TemplateService templateService;
    private final OpenAiService openAiService;
    private final CurrentUserService currentUserService;
    private final FileService fileService;

    public PostService(ProductBriefRepository briefRepository,
                       GeneratedPostRepository postRepository,
                       TemplateService templateService,
                       OpenAiService openAiService,
                       CurrentUserService currentUserService,
                       FileService fileService) {
        this.briefRepository = briefRepository;
        this.postRepository = postRepository;
        this.templateService = templateService;
        this.openAiService = openAiService;
        this.currentUserService = currentUserService;
        this.fileService = fileService;
    }

    @Transactional
    public GeneratePostResponse generate(GeneratePostRequest request) {
        UUID userId = currentUserService.requireCurrentUserId();
        IndustryTemplate template = templateService.findByCode(request.industryCode()).orElse(null);

        ProductBrief brief = new ProductBrief();
        brief.setUserId(userId);
        brief.setProductName(request.productName());
        brief.setProductDescription(request.productDescription());
        brief.setPrice(request.price());
        brief.setIndustryCode(request.industryCode());
        brief.setTargetAudience(request.targetAudience());
        brief.setHighlights(request.highlights());
        brief.setGoal(request.goal());
        brief.setTone(request.tone());
        brief.setPlatforms(request.platforms());
        brief.setAdditionalInfo(combineAdditionalInfo(request.productDescription(), request.additionalInfo()));
        brief = briefRepository.save(brief);

        AiPostResult aiResult = openAiService.generatePost(request, template);

        GeneratedPost post = new GeneratedPost();
        post.setBriefId(brief.getId());
        post.setUserId(userId);
        Map<String, Object> storedContents = new LinkedHashMap<>(
                aiResult.platformContents() == null ? Map.of() : aiResult.platformContents()
        );
        storedContents.put(TITLE_KEY, aiResult.title());
        post.setPlatformContents(storedContents);
        post.setHashtags(aiResult.hashtags() == null ? List.of() : aiResult.hashtags());
        post.setCta(aiResult.cta());
        post.setImageSuggestion(aiResult.imageSuggestion());
        post.setStatus(PostStatus.DRAFT);
        post = postRepository.save(post);

        String imageUrl = fileService.attachToPost(request.imageIds(), userId, brief.getId(), post.getId());
        if (imageUrl != null) {
            post.setImageUrl(imageUrl);
            post = postRepository.save(post);
        }

        return toResponse(post);
    }

    public GeneratedPost getPost(UUID postId) {
        UUID userId = currentUserService.requireCurrentUserId();
        return postRepository.findByIdAndUserId(postId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bài viết"));
    }

    public GeneratePostResponse getPostResponse(UUID postId) {
        return toResponse(getPost(postId));
    }

    @Transactional
    public GeneratePostResponse update(UUID postId, UpdatePostRequest request) {
        GeneratedPost post = getPost(postId);
        Map<String, Object> contents = new LinkedHashMap<>();
        if (request.platformContents() != null && !request.platformContents().isEmpty()) {
            contents.putAll(request.platformContents());
        } else if (post.getPlatformContents() != null) {
            contents.putAll(post.getPlatformContents());
        }
        if (request.title() != null) {
            contents.put(TITLE_KEY, request.title().trim());
        } else if (post.getPlatformContents() != null && post.getPlatformContents().containsKey(TITLE_KEY)) {
            contents.put(TITLE_KEY, post.getPlatformContents().get(TITLE_KEY));
        }
        post.setPlatformContents(contents);
        post.setHashtags(request.hashtags() == null ? List.of() : request.hashtags());
        post.setCta(request.cta());
        return toResponse(postRepository.save(post));
    }

    private GeneratePostResponse toResponse(GeneratedPost post) {
        return new GeneratePostResponse(
                post.getId(), post.getBriefId(), titleFrom(post.getPlatformContents()), publicPlatformContents(post.getPlatformContents()), post.getHashtags(),
                post.getCta(), post.getImageSuggestion(), post.getImageUrl(), post.getStatus().name()
        );
    }

    private String titleFrom(Map<String, Object> platformContents) {
        if (platformContents == null || platformContents.isEmpty()) {
            return null;
        }
        if (platformContents.containsKey(TITLE_KEY)) {
            Object title = platformContents.get(TITLE_KEY);
            return title == null ? null : title.toString();
        }
        for (String key : List.of("FACEBOOK", "INSTAGRAM", "THREADS")) {
            Object value = platformContents.get(key);
            if (value instanceof Map<?, ?> map) {
                Object title = map.get("title");
                if (title != null && !title.toString().isBlank()) {
                    return title.toString();
                }
            }
        }
        return null;
    }

    private Map<String, Object> publicPlatformContents(Map<String, Object> platformContents) {
        if (platformContents == null || platformContents.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : List.of("FACEBOOK", "INSTAGRAM", "THREADS")) {
            Object value = platformContents.get(key);
            if (value instanceof Map<?, ?>) {
                result.put(key, value);
            }
        }
        return result;
    }

    private String combineAdditionalInfo(String productDescription, String additionalInfo) {
        String description = productDescription == null || productDescription.isBlank()
                ? ""
                : "Mô tả sản phẩm: " + productDescription.trim();
        String note = additionalInfo == null || additionalInfo.isBlank()
                ? ""
                : "Ghi chú thêm: " + additionalInfo.trim();
        if (description.isBlank()) {
            return note.isBlank() ? null : note;
        }
        if (note.isBlank()) {
            return description;
        }
        return description + "\n" + note;
    }
}
