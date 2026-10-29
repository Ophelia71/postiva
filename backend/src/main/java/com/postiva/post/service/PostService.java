package com.postiva.post.service;

import com.postiva.ai.dto.AiPostResult;
import com.postiva.ai.dto.ShopContext;
import com.postiva.business.entity.BusinessProfile;
import com.postiva.business.service.ShopProfileService;
import com.postiva.ai.service.OpenAiService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.postiva.auth.security.PersistentRateLimiter;
import com.postiva.config.OpenAiProperties;
import com.postiva.usage.AiUsageService;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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
    private final AiUsageService usage;
    private final OpenAiProperties openAiProperties;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;
    private final ShopProfileService shopProfiles;

    public PostService(ProductBriefRepository briefRepository,
                       GeneratedPostRepository postRepository,
                       TemplateService templateService,
                       OpenAiService openAiService,
                       CurrentUserService currentUserService,
                       FileService fileService, AiUsageService usage, OpenAiProperties openAiProperties,
                       ObjectMapper json, PlatformTransactionManager transactionManager,
                       ShopProfileService shopProfiles) {
        this.shopProfiles = shopProfiles;
        this.briefRepository = briefRepository;
        this.postRepository = postRepository;
        this.templateService = templateService;
        this.openAiService = openAiService;
        this.currentUserService = currentUserService;
        this.fileService = fileService;
        this.usage = usage;
        this.openAiProperties = openAiProperties;
        this.json = json;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public GeneratePostResponse generate(GeneratePostRequest request, UUID idempotencyKey) {
        return generateForUser(currentUserService.requireCurrentUserId(), request, idempotencyKey);
    }

    /** Same as {@link #generate} for work that runs outside a request, such as weekly plan drafts. */
    public GeneratePostResponse generateForUser(UUID userId, GeneratePostRequest request, UUID idempotencyKey) {
        if (idempotencyKey == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thiếu Idempotency-Key");
        if (!openAiProperties.enabled()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Dịch vụ AI chưa sẵn sàng");
        fileService.validateAttachments(request.imageIds(), userId);
        BusinessProfile profile = shopProfiles.forUser(userId).orElse(null);
        // The shop's own industry fills in for drafts that do not name one.
        String industryCode = request.industryCode() != null && !request.industryCode().isBlank()
                ? request.industryCode() : profile == null ? null : ShopProfileService.templateCode(profile);
        IndustryTemplate template = industryCode == null ? null : templateService.findByCode(industryCode).orElse(null);
        ShopContext shop = profile == null ? null : shopProfiles.context(profile);
        String fingerprint;
        try { fingerprint = PersistentRateLimiter.sha256(json.writeValueAsString(request)); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("Nội dung yêu cầu không hợp lệ", e); }
        var reservation = usage.reserve(userId, idempotencyKey, fingerprint, openAiProperties.model());
        if (reservation.existingPostId() != null) {
            return toResponse(postRepository.findByIdAndUserId(reservation.existingPostId(), userId)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bài viết")));
        }
        try {
            // External I/O is outside the database transaction and does not hold a connection/row lock.
            AiPostResult generated = openAiService.generatePost(request, template, shop,
                    providerUsage -> usage.recordProviderUsage(reservation.requestId(), providerUsage));
            AiPostResult result = profile == null ? generated
                    : ShopSignature.apply(generated, profile.getSignature(), profile.getBrandHashtags());
            return transactions.execute(status -> {
                GeneratePostResponse saved = saveGenerated(request, userId, result);
                usage.succeed(userId, reservation.requestId(), saved.postId());
                return saved;
            });
        } catch (RuntimeException e) {
            boolean uncertain = !(e instanceof ResponseStatusException status && status.getStatusCode().is4xxClientError());
            try { usage.fail(userId, reservation.requestId(), uncertain, "GENERATION_FAILED"); }
            catch (RuntimeException failure) { e.addSuppressed(failure); }
            throw e;
        }
    }

    private GeneratePostResponse saveGenerated(GeneratePostRequest request, UUID userId, AiPostResult aiResult) {

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

        postRepository.flush();
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

    /** Posts of one user by id, for screens that show many drafts at once. Missing ids are left out. */
    @Transactional(readOnly = true)
    public Map<UUID, GeneratePostResponse> getPostResponses(UUID userId, List<UUID> postIds) {
        Map<UUID, GeneratePostResponse> responses = new LinkedHashMap<>();
        for (UUID postId : postIds) {
            postRepository.findByIdAndUserId(postId, userId).ifPresent(post -> responses.put(postId, toResponse(post)));
        }
        return responses;
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
