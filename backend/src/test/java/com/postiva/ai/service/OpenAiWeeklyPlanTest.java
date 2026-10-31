package com.postiva.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.postiva.ai.dto.WeeklyIdeasResult;
import com.postiva.ai.dto.WeeklyPlanPromptInput;
import com.postiva.common.Platform;
import com.postiva.config.OpenAiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiWeeklyPlanTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OpenAiService service = new OpenAiService(
            new OpenAiProperties("test-key", "gpt-6-luna"), new PromptBuilder(), objectMapper, WebClient.builder());

    @Test
    void payloadUsesStrictSchemaAndCarriesFindingsAndSlots() {
        Map<String, Object> payload = service.buildWeeklyIdeasPayload(input());

        Map<?, ?> jsonSchema = (Map<?, ?>) ((Map<?, ?>) payload.get("response_format")).get("json_schema");
        assertEquals(true, jsonSchema.get("strict"));
        Map<?, ?> schema = (Map<?, ?>) jsonSchema.get("schema");
        assertEquals(List.of("strategy", "items"), schema.get("required"));
        Map<?, ?> item = (Map<?, ?>) ((Map<?, ?>) ((Map<?, ?>) schema.get("properties")).get("items")).get("items");
        assertEquals(List.of("date", "time", "postType", "topic", "angle", "reason"), item.get("required"));
        assertEquals(false, item.get("additionalProperties"));

        String prompt = (String) ((Map<?, ?>) ((List<?>) payload.get("messages")).get(1)).get("content");
        assertTrue(prompt.contains("Bài video đạt tương tác gấp 3 lần bài chỉ có chữ."));
        assertTrue(prompt.contains("2026-11-02 (Thứ Hai) 19:00"));
        assertTrue(prompt.contains("Facebook Page"));
    }

    @Test
    void parsesIdeasDropsBlankTopicsAndCapsToRequestedDays() throws Exception {
        String content = objectMapper.writeValueAsString(Map.of(
                "strategy", " Tuần ra mắt ",
                "items", List.of(
                        idea("2026-11-02", "Giới thiệu"),
                        idea("2026-11-04", " "),
                        idea("2026-11-05", "Feedback khách"),
                        idea("2026-11-06", "Ưu đãi cuối tuần"))));

        WeeklyIdeasResult result = service.parseWeeklyIdeasResponse(completion(content), 2);

        assertEquals("Tuần ra mắt", result.strategy());
        assertEquals(List.of("Giới thiệu", "Feedback khách"), result.items().stream().map(WeeklyIdeasResult.Idea::topic).toList());
    }

    @Test
    void rejectsPlanWithoutUsableIdeas() throws Exception {
        String content = objectMapper.writeValueAsString(Map.of("strategy", "x", "items", List.of(idea("2026-11-02", ""))));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.parseWeeklyIdeasResponse(completion(content), 3));
        assertEquals(502, error.getStatusCode().value());
    }

    @Test
    void refusalIsReportedAsUnprocessable() throws Exception {
        String response = objectMapper.writeValueAsString(Map.of("choices", List.of(Map.of(
                "message", Map.of("refusal", "no", "content", "")))));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.parseWeeklyIdeasResponse(response, 3));
        assertEquals(422, error.getStatusCode().value());
    }

    private Map<String, String> idea(String date, String topic) {
        return Map.of("date", date, "time", "19:00", "postType", "Giới thiệu", "topic", topic,
                "angle", "Kể chuyện", "reason", "Lý do");
    }

    private String completion(String content) throws Exception {
        return objectMapper.writeValueAsString(Map.of("choices", List.of(Map.of("message", Map.of("content", content)))));
    }

    private WeeklyPlanPromptInput input() {
        return new WeeklyPlanPromptInput("Cà phê muối", "Pha phin", "35.000đ", "fnb", "Dân văn phòng", "Giao 30 phút",
                "Tăng đơn", "Thân thiện", Platform.FACEBOOK,
                List.of("2026-11-02 (Thứ Hai) 19:00", "2026-11-05 (Thứ Năm) 19:00"),
                List.of("Bài video đạt tương tác gấp 3 lần bài chỉ có chữ."), List.of("Bài cũ hay nhất"),
                List.of(), List.of(), null);
    }
}
