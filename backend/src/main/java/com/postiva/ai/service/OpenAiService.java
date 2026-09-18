package com.postiva.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.postiva.ai.dto.AiPostResult;
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
        if (!properties.enabled()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Chưa cấu hình OPENAI_API_KEY trong backend/.env"
            );
        }

        String response = callOpenAi(buildRequestPayload(request, template));
        return parseOpenAiResponse(response, request);
    }

    Map<String, Object> buildRequestPayload(GeneratePostRequest request, IndustryTemplate template) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", properties.model());
        payload.put("messages", List.of(
                Map.of(
                        "role", "system",
                        "content", "Bạn là chuyên gia social media. Hãy tuân thủ chính xác JSON schema được cung cấp và chỉ dùng dữ liệu sản phẩm trong yêu cầu."
                ),
                Map.of(
                        "role", "user",
                        "content", promptBuilder.buildGeneratePostPrompt(request, template)
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
        payload.put("temperature", 0.7);
        payload.put("max_tokens", 1800);
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

    private String callOpenAi(Map<String, Object> payload) {
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
