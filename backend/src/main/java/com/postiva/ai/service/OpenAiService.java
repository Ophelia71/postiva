package com.postiva.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.postiva.ai.dto.AiPostResult;
import com.postiva.ai.dto.ProviderTokenUsage;
import com.postiva.ai.dto.ShopContext;
import com.postiva.ai.dto.WeeklyIdeasResult;
import com.postiva.ai.dto.WeeklyPlanPromptInput;
import com.postiva.common.Platform;
import com.postiva.config.OpenAiProperties;
import com.postiva.post.dto.GeneratePostRequest;
import com.postiva.template.entity.IndustryTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@Service
public class OpenAiService {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(90);
    private static final String OPENAI_BASE_URL = "https://api.openai.com/v1";

    private final OpenAiProperties properties;
    private final PromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public OpenAiService(OpenAiProperties properties,
                         PromptBuilder promptBuilder,
                         ObjectMapper objectMapper,
                         WebClient.Builder webClientBuilder) {
        this.properties = properties;
        this.promptBuilder = promptBuilder;
        this.objectMapper = objectMapper;

        WebClient.Builder configuredBuilder = webClientBuilder.clone().baseUrl(OPENAI_BASE_URL);
        if (properties.enabled()) {
            configuredBuilder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey());
        }
        this.webClient = configuredBuilder.build();
    }

    public AiPostResult generatePost(GeneratePostRequest request, IndustryTemplate template) {
        return generatePost(request, template, null);
    }

    public AiPostResult generatePost(GeneratePostRequest request, IndustryTemplate template,
                                     Consumer<ProviderTokenUsage> usageObserver) {
        return generatePost(request, template, null, usageObserver);
    }

    /** {@code shop} is the saved shop profile, or null when the shop has not set one up. */
    public AiPostResult generatePost(GeneratePostRequest request, IndustryTemplate template, ShopContext shop,
                                     Consumer<ProviderTokenUsage> usageObserver) {
        if (!properties.enabled()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Chưa cấu hình OPENAI_API_KEY trong backend/.env"
            );
        }

        String response = callOpenAi(buildRequestPayload(request, template, shop), usageObserver);
        return parseOpenAiResponse(response, request);
    }

    Map<String, Object> buildRequestPayload(GeneratePostRequest request, IndustryTemplate template) {
        return buildRequestPayload(request, template, null);
    }

    Map<String, Object> buildRequestPayload(GeneratePostRequest request, IndustryTemplate template, ShopContext shop) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", properties.model());
        payload.put("messages", List.of(
                Map.of(
                        "role", "system",
                        "content", "Bạn là chuyên gia social media. Hãy tuân thủ chính xác JSON schema được cung cấp và chỉ dùng dữ liệu sản phẩm trong yêu cầu."
                ),
                Map.of(
                        "role", "user",
                        "content", promptBuilder.buildGeneratePostPrompt(request, template, shop)
                )
        ));
        payload.put("response_format", Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                        "name", "postiva_product_post",
                        "strict", true,
                        "schema", buildResponseSchema(request.platforms())
                )
        ));
        // GPT-6 reasoning requests reject sampling controls unless reasoning_effort is "none".
        // Keep a low reasoning budget for content writing and use the current completion limit.
        if (properties.model() != null && properties.model().startsWith("gpt-6")) {
            payload.put("reasoning_effort", "low");
        } else {
            payload.put("temperature", 0.7);
        }
        payload.put("max_completion_tokens", 1800);
        return payload;
    }

    AiPostResult parseOpenAiResponse(String response, GeneratePostRequest request) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw invalidResponse();
            }

            JsonNode message = choices.get(0).path("message");
            String refusal = message.path("refusal").asText("");
            if (!refusal.isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "OpenAI từ chối tạo nội dung cho yêu cầu này"
                );
            }

            String content = message.path("content").asText("");
            if (content.isBlank()) {
                throw invalidResponse();
            }
            return normalizeResult(objectMapper.readValue(content, AiPostResult.class), request.platforms());
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidResponse(exception);
        }
    }

    public WeeklyIdeasResult generateWeeklyIdeas(WeeklyPlanPromptInput input, Consumer<ProviderTokenUsage> usageObserver) {
        if (!properties.enabled()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Chưa cấu hình OPENAI_API_KEY trong backend/.env"
            );
        }
        return parseWeeklyIdeasResponse(callOpenAi(buildWeeklyIdeasPayload(input), usageObserver), input.slots().size());
    }

    Map<String, Object> buildWeeklyIdeasPayload(WeeklyPlanPromptInput input) {
        Map<String, Object> idea = new LinkedHashMap<>();
        for (String field : List.of("date", "time", "postType", "topic", "angle", "reason")) {
            idea.put(field, Map.of("type", "string"));
        }
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("strategy", Map.of("type", "string"));
        properties.put("items", Map.of(
                "type", "array",
                "items", objectSchema(idea, List.copyOf(idea.keySet()))
        ));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", this.properties.model());
        payload.put("messages", List.of(
                Map.of("role", "system", "content",
                        "Bạn là chuyên gia marketing social media. Hãy tuân thủ chính xác JSON schema được cung cấp và chỉ dùng dữ liệu trong yêu cầu."),
                Map.of("role", "user", "content", promptBuilder.buildWeeklyPlanPrompt(input))
        ));
        payload.put("response_format", Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                        "name", "postiva_weekly_plan",
                        "strict", true,
                        "schema", objectSchema(properties, List.of("strategy", "items"))
                )
        ));
        if (this.properties.model() != null && this.properties.model().startsWith("gpt-6")) {
            payload.put("reasoning_effort", "low");
        } else {
            payload.put("temperature", 0.8);
        }
        payload.put("max_completion_tokens", 1800);
        return payload;
    }

    WeeklyIdeasResult parseWeeklyIdeasResponse(String response, int expectedItems) {
        try {
            JsonNode message = objectMapper.readTree(response).path("choices").path(0).path("message");
            if (!message.path("refusal").asText("").isBlank()) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "OpenAI từ chối lên kế hoạch cho yêu cầu này");
            }
            String content = message.path("content").asText("");
            if (content.isBlank()) {
                throw invalidWeeklyPlan(null);
            }
            WeeklyIdeasResult result = objectMapper.readValue(content, WeeklyIdeasResult.class);
            if (result == null || result.items() == null || result.items().isEmpty()) {
                throw invalidWeeklyPlan(null);
            }
            List<WeeklyIdeasResult.Idea> ideas = new ArrayList<>();
            for (WeeklyIdeasResult.Idea idea : result.items()) {
                if (idea == null || isBlank(idea.topic())) continue;
                ideas.add(new WeeklyIdeasResult.Idea(trim(idea.date()), trim(idea.time()), trim(idea.postType()),
                        idea.topic().trim(), trim(idea.angle()), trim(idea.reason())));
                if (ideas.size() == expectedItems) break;
            }
            if (ideas.isEmpty()) {
                throw invalidWeeklyPlan(null);
            }
            return new WeeklyIdeasResult(trim(result.strategy()), List.copyOf(ideas));
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidWeeklyPlan(exception);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private ResponseStatusException invalidWeeklyPlan(Exception cause) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OpenAI trả về kế hoạch tuần không đúng định dạng", cause);
    }

    private String callOpenAi(Map<String, Object> payload, Consumer<ProviderTokenUsage> usageObserver) {
        try {
            String response = webClient.post()
                    .uri("/chat/completions")
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(REQUEST_TIMEOUT);
            if (response == null || response.isBlank()) {
                throw invalidResponse();
            }
            if (usageObserver != null) usageObserver.accept(parseProviderUsage(response));
            return response;
        } catch (WebClientResponseException exception) {
            throw mapOpenAiError(exception);
        } catch (WebClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Không thể kết nối tới OpenAI. Hãy kiểm tra mạng và thử lại.",
                    exception
            );
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    "OpenAI phản hồi quá lâu. Hãy thử lại.",
                    exception
            );
        }
    }

    ProviderTokenUsage parseProviderUsage(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode usage = root.path("usage");
            if (!usage.isObject() || !usage.path("prompt_tokens").canConvertToInt()
                    || !usage.path("completion_tokens").canConvertToInt()) {
                return new ProviderTokenUsage(null, null, null, root.path("model").asText(null), root.path("id").asText(null));
            }
            int promptTokens = usage.path("prompt_tokens").asInt();
            int outputTokens = usage.path("completion_tokens").asInt();
            int cachedTokens = usage.path("prompt_tokens_details").path("cached_tokens").canConvertToInt()
                    ? usage.path("prompt_tokens_details").path("cached_tokens").asInt() : 0;
            int cacheWriteTokens = usage.path("prompt_tokens_details").path("cache_write_tokens").canConvertToInt()
                    ? usage.path("prompt_tokens_details").path("cache_write_tokens").asInt() : 0;
            return new ProviderTokenUsage(promptTokens, cachedTokens, cacheWriteTokens, outputTokens,
                    root.path("model").asText(null), root.path("id").asText(null));
        } catch (Exception exception) {
            return new ProviderTokenUsage(null, null, null, null, null);
        }
    }

    private ResponseStatusException mapOpenAiError(WebClientResponseException exception) {
        int status = exception.getStatusCode().value();
        if (status == 401 || status == 403) {
            return new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "OpenAI API key không hợp lệ hoặc không có quyền sử dụng model đã cấu hình.",
                    exception
            );
        }
        if (status == 429) {
            return new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "OpenAI đang giới hạn yêu cầu hoặc tài khoản đã hết quota. Hãy kiểm tra Billing/Usage.",
                    exception
            );
        }
        if (status == 400 || status == 404) {
            return new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "OpenAI từ chối yêu cầu. Hãy kiểm tra OPENAI_MODEL và quyền truy cập model.",
                    exception
            );
        }
        return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "OpenAI tạm thời không xử lý được yêu cầu. Hãy thử lại sau.",
                exception
        );
    }

    private Map<String, Object> buildResponseSchema(List<Platform> platforms) {
        Map<String, Object> platformProperties = new LinkedHashMap<>();
        List<String> requiredPlatforms = new ArrayList<>();
        for (Platform platform : platforms) {
            String name = platform.name();
            if (!platformProperties.containsKey(name)) {
                platformProperties.put(name, platformContentSchema(platform));
                requiredPlatforms.add(name);
            }
        }

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("title", Map.of("type", "string"));
        properties.put("platformContents", objectSchema(platformProperties, requiredPlatforms));
        properties.put("hashtags", Map.of(
                "type", "array",
                "items", Map.of("type", "string")
        ));
        properties.put("cta", Map.of("type", "string"));
        properties.put("imageSuggestion", Map.of("type", "string"));
        return objectSchema(properties, List.of(
                "title", "platformContents", "hashtags", "cta", "imageSuggestion"
        ));
    }

    private Map<String, Object> platformContentSchema(Platform platform) {
        String contentField = platform == Platform.INSTAGRAM ? "caption" : "content";
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("title", Map.of("type", "string"));
        properties.put(contentField, Map.of("type", "string"));
        return objectSchema(properties, List.of("title", contentField));
    }

    private Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    private AiPostResult normalizeResult(AiPostResult result, List<Platform> platforms) {
        if (result == null || result.platformContents() == null || result.hashtags() == null) {
            throw invalidResponse();
        }

        Map<String, Object> normalizedContents = new LinkedHashMap<>();
        for (Platform platform : platforms) {
            Object rawContent = result.platformContents().get(platform.name());
            if (!(rawContent instanceof Map<?, ?> values)) {
                throw invalidResponse();
            }
            String contentField = platform == Platform.INSTAGRAM ? "caption" : "content";
            Map<String, Object> normalizedPlatform = new LinkedHashMap<>();
            normalizedPlatform.put("title", requiredText(values.get("title")));
            normalizedPlatform.put(contentField, requiredText(values.get(contentField)));
            normalizedContents.put(platform.name(), normalizedPlatform);
        }

        Set<String> hashtags = new LinkedHashSet<>();
        for (String value : result.hashtags()) {
            if (value == null) {
                continue;
            }
            String hashtag = value.trim().replaceAll("\\s+", "");
            if (hashtag.isBlank()) {
                continue;
            }
            hashtags.add(hashtag.startsWith("#") ? hashtag : "#" + hashtag);
            if (hashtags.size() == 20) {
                break;
            }
        }
        if (hashtags.isEmpty()) {
            throw invalidResponse();
        }

        return new AiPostResult(
                requiredText(result.title()),
                normalizedContents,
                List.copyOf(hashtags),
                requiredText(result.cta()),
                requiredText(result.imageSuggestion())
        );
    }

    private String requiredText(Object value) {
        String text = value == null ? "" : value.toString().trim();
        if (text.isBlank()) {
            throw invalidResponse();
        }
        return text;
    }

    private ResponseStatusException invalidResponse() {
        return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "OpenAI trả về dữ liệu không đúng schema bài viết"
        );
    }

    private ResponseStatusException invalidResponse(Exception cause) {
        return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "OpenAI trả về dữ liệu không đúng schema bài viết",
                cause
        );
    }
}
