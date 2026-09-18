package com.postiva.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.postiva.ai.dto.AiPostResult;
import com.postiva.common.Platform;
import com.postiva.config.OpenAiProperties;
import com.postiva.post.dto.GeneratePostRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void requestPayloadUsesStrictSchemaForSelectedPlatforms() {
        OpenAiService service = service("test-key");

        Map<String, Object> payload = service.buildRequestPayload(
                request(List.of(Platform.FACEBOOK, Platform.THREADS)),
                null
        );

        Map<?, ?> responseFormat = map(payload.get("response_format"));
        assertEquals("json_schema", responseFormat.get("type"));
        Map<?, ?> jsonSchema = map(responseFormat.get("json_schema"));
        assertEquals(true, jsonSchema.get("strict"));

        Map<?, ?> schema = map(jsonSchema.get("schema"));
        assertEquals(false, schema.get("additionalProperties"));
        Map<?, ?> rootProperties = map(schema.get("properties"));
        Map<?, ?> platformContents = map(rootProperties.get("platformContents"));
        Map<?, ?> platformProperties = map(platformContents.get("properties"));

        assertTrue(platformProperties.containsKey("FACEBOOK"));
        assertTrue(platformProperties.containsKey("THREADS"));
        assertFalse(platformProperties.containsKey("INSTAGRAM"));
    }

    @Test
    void responseIsParsedAndNormalizedForSelectedPlatforms() throws Exception {
        OpenAiService service = service("test-key");
        String content = """
                {
                  "title": "Bộ sưu tập mới",
                  "platformContents": {
                    "FACEBOOK": {"title": "Facebook title", "content": "Facebook content"},
                    "INSTAGRAM": {"title": "Instagram title", "caption": "Instagram caption"}
                  },
                  "hashtags": ["Postiva", " #Khuyen Mai "],
                  "cta": "Nhắn tin ngay",
                  "imageSuggestion": "Ảnh sản phẩm nền sáng"
                }
                """;
        String response = objectMapper.writeValueAsString(Map.of(
                "choices", List.of(Map.of(
                        "message", Map.of("content", content)
                ))
        ));

        AiPostResult result = service.parseOpenAiResponse(
                response,
                request(List.of(Platform.FACEBOOK, Platform.INSTAGRAM))
        );

        assertEquals("Bộ sưu tập mới", result.title());
        assertEquals(List.of("#Postiva", "#KhuyenMai"), result.hashtags());
        assertEquals(2, result.platformContents().size());
    }

    @Test
    void missingApiKeyDoesNotSilentlyReturnMockContent() {
        OpenAiService service = service("");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.generatePost(request(List.of(Platform.FACEBOOK)), null)
        );

        assertEquals(503, exception.getStatusCode().value());
        assertTrue(exception.getReason().contains("OPENAI_API_KEY"));
    }

    private OpenAiService service(String apiKey) {
        return new OpenAiService(
                new OpenAiProperties(apiKey, "gpt-4o-mini"),
                new PromptBuilder(),
                objectMapper,
                WebClient.builder()
        );
    }

    private GeneratePostRequest request(List<Platform> platforms) {
        return new GeneratePostRequest(
                "Sản phẩm mẫu",
                "Mô tả sản phẩm mẫu",
                "199.000đ",
                null,
                "Khách hàng trẻ",
                "Thiết kế đẹp, dễ sử dụng",
                "Tăng inbox",
                "Thân thiện",
                platforms,
                null,
                List.of()
        );
    }

    private Map<?, ?> map(Object value) {
        return (Map<?, ?>) value;
    }
}
