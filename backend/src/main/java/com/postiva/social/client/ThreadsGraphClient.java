package com.postiva.social.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.postiva.common.Platform;
import com.postiva.social.insights.PostMetrics;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ThreadsGraphClient {
    private static final int STATUS_ATTEMPTS = 30;
    private static final long STATUS_DELAY_MILLIS = 2_000;

    private final WebClient tokenClient;
    private final WebClient apiClient;
    private final ObjectMapper objectMapper;

    public ThreadsGraphClient(WebClient.Builder builder, ObjectMapper objectMapper) {
        this.tokenClient = builder.clone().baseUrl("https://graph.threads.net").build();
        this.apiClient = builder.clone().baseUrl("https://graph.threads.net/v1.0").build();
        this.objectMapper = objectMapper;
    }

    public AccessToken refreshLongLived(String accessToken) {
        String body = tokenClient.get()
                .uri(uri -> uri.path("/refresh_access_token")
                        .queryParam("grant_type", "th_refresh_token")
                        .queryParam("access_token", accessToken)
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .map(responseBody -> new MetaApiException(response.statusCode().value(),
                                metaErrorMessage(responseBody))))
                .bodyToMono(String.class)
                .block();
        return parseAccessToken(body, 0);
    }

    public ThreadsProfile getMe(String accessToken) {
        String body = apiClient.get()
                .uri(uri -> uri.path("/me")
                        .queryParam("fields", "id,username")
                        .queryParam("access_token", accessToken)
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .map(responseBody -> new MetaApiException(response.statusCode().value(),
                                metaErrorMessage(responseBody))))
                .bodyToMono(String.class)
                .block();
        try {
            JsonNode root = objectMapper.readTree(body);
            return new ThreadsProfile(root.path("id").asText(), root.path("username").asText());
        } catch (Exception exception) {
            throw new IllegalStateException("Threads trả về hồ sơ không hợp lệ", exception);
        }
    }

    public String createTextContainer(String accessToken, String text) {
        MultiValueMap<String, String> form = baseForm(accessToken);
        form.add("media_type", "TEXT");
        form.add("text", text);
        return createContainer(form);
    }

    public String createMediaContainer(String accessToken,
                                       String text,
                                       String mediaType,
                                       String mediaUrl,
                                       boolean carouselItem) {
        MultiValueMap<String, String> form = baseForm(accessToken);
        form.add("media_type", mediaType);
        form.add(mediaUrlField(mediaType), mediaUrl);
        if (carouselItem) {
            form.add("is_carousel_item", "true");
        } else if (text != null && !text.isBlank()) {
            form.add("text", text);
        }
        return createContainer(form);
    }

    public String createCarouselContainer(String accessToken, String text, List<String> children) {
        MultiValueMap<String, String> form = baseForm(accessToken);
        form.add("media_type", "CAROUSEL");
        form.add("children", String.join(",", children));
        if (text != null && !text.isBlank()) {
            form.add("text", text);
        }
        return createContainer(form);
    }

    public String publishContainer(String accessToken, String containerId) {
        MultiValueMap<String, String> form = baseForm(accessToken);
        form.add("creation_id", containerId);
        String body = apiClient.post()
                .uri("/me/threads_publish")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .map(responseBody -> new MetaApiException(response.statusCode().value(),
                                metaErrorMessage(responseBody))))
                .bodyToMono(String.class)
                .block();
        return extractId(body, "Threads không trả về ID bài đăng");
    }

    /** Recent posts of the token's profile, without engagement (Threads only exposes that through insights). */
    public List<PostMetrics> getRecentThreads(String accessToken, int limit) {
        String body = apiClient.get()
                .uri(uri -> uri.path("/me/threads")
                        .queryParam("fields", "id,text,media_type,timestamp,permalink")
                        .queryParam("limit", limit)
                        .queryParam("access_token", accessToken)
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .map(responseBody -> new MetaApiException(response.statusCode().value(),
                                metaErrorMessage(responseBody))))
                .bodyToMono(String.class)
                .block();
        try {
            List<PostMetrics> posts = new ArrayList<>();
            for (JsonNode node : objectMapper.readTree(body).path("data")) {
                String id = node.path("id").asText();
                // Reposts of other people's posts have no insights and say nothing about this shop's content.
                if (id.isBlank() || "REPOST_FACADE".equalsIgnoreCase(node.path("media_type").asText())) {
                    continue;
                }
                posts.add(new PostMetrics(
                        id,
                        Platform.THREADS,
                        parseTimestamp(node.path("timestamp").asText()),
                        node.path("text").asText(""),
                        node.path("media_type").asText("TEXT_POST"),
                        null, null, null, null,
                        node.path("permalink").asText(null)
                ));
            }
            return posts;
        } catch (Exception exception) {
            throw new IllegalStateException("Threads trả về danh sách bài không hợp lệ", exception);
        }
    }

    /** Lifetime metrics of one post. Needs threads_manage_insights; callers treat a failure as "no data". */
    public Map<String, Integer> getThreadInsights(String mediaId, String accessToken) {
        String body = apiClient.get()
                .uri(uri -> uri.path("/{mediaId}/insights")
                        .queryParam("metric", "views,likes,replies,reposts,quotes")
                        .queryParam("access_token", accessToken)
                        .build(mediaId))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .map(responseBody -> new MetaApiException(response.statusCode().value(),
                                metaErrorMessage(responseBody))))
                .bodyToMono(String.class)
                .block();
        try {
            Map<String, Integer> metrics = new HashMap<>();
            for (JsonNode metric : objectMapper.readTree(body).path("data")) {
                JsonNode value = metric.path("values").path(0).path("value");
                if (!value.canConvertToInt()) {
                    value = metric.path("total_value").path("value");
                }
                if (value.canConvertToInt()) {
                    metrics.put(metric.path("name").asText(), value.asInt());
                }
            }
            return metrics;
        } catch (Exception exception) {
            throw new IllegalStateException("Threads trả về insights không hợp lệ", exception);
        }
    }

    private static Instant parseTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")).toInstant();
        } catch (RuntimeException exception) {
            try {
                return OffsetDateTime.parse(value).toInstant();
            } catch (RuntimeException invalid) {
                return null;
            }
        }
    }

    public void awaitContainerReady(String containerId, String accessToken) {
        for (int attempt = 0; attempt < STATUS_ATTEMPTS; attempt++) {
            ContainerStatus status = getContainerStatus(containerId, accessToken);
            if ("FINISHED".equals(status.code()) || "PUBLISHED".equals(status.code())) {
                return;
            }
            if ("ERROR".equals(status.code()) || "EXPIRED".equals(status.code())) {
                throw new IllegalStateException("Threads xử lý media thất bại: " + status.description());
            }
            pauseBeforeRetry();
        }
        throw new IllegalStateException("Threads xử lý media quá lâu. Hãy thử đăng lại sau.");
    }

    private String createContainer(MultiValueMap<String, String> form) {
        String body = apiClient.post()
                .uri("/me/threads")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .map(responseBody -> new MetaApiException(response.statusCode().value(),
                                metaErrorMessage(responseBody))))
                .bodyToMono(String.class)
                .block();
        return extractId(body, "Threads không trả về container ID");
    }

    private ContainerStatus getContainerStatus(String containerId, String accessToken) {
        String body = apiClient.get()
                .uri(uri -> uri.path("/{containerId}")
                        .queryParam("fields", "status,error_message")
                        .queryParam("access_token", accessToken)
                        .build(containerId))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .map(responseBody -> new MetaApiException(response.statusCode().value(),
                                metaErrorMessage(responseBody))))
                .bodyToMono(String.class)
                .block();
        try {
            JsonNode root = objectMapper.readTree(body);
            return new ContainerStatus(
                    root.path("status").asText("").toUpperCase(),
                    root.path("error_message").asText("Đang xử lý media")
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Threads trả về trạng thái media không hợp lệ", exception);
        }
    }

    private String extractId(String body, String errorMessage) {
        try {
            String id = objectMapper.readTree(body).path("id").asText();
            if (id.isBlank()) {
                throw new IllegalStateException(errorMessage);
            }
            return id;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Threads trả về dữ liệu publish không hợp lệ", exception);
        }
    }

    private MultiValueMap<String, String> baseForm(String accessToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("access_token", accessToken);
        return form;
    }

    private String mediaUrlField(String mediaType) {
        return "IMAGE".equalsIgnoreCase(mediaType) ? "image_url" : "video_url";
    }

    private String metaErrorMessage(String body) {
        try {
            String message = objectMapper.readTree(body).path("error").path("message").asText();
            if (message != null && !message.isBlank()) {
                return "Threads từ chối yêu cầu: " + message;
            }
        } catch (Exception ignored) {
        }
        return "Threads từ chối yêu cầu. Kiểm tra access token, quyền threads_content_publish và media.";
    }

    private void pauseBeforeRetry() {
        try {
            Thread.sleep(STATUS_DELAY_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Đã dừng khi đang chờ Threads xử lý media", exception);
        }
    }

    private AccessToken parseAccessToken(String body, long defaultExpiresIn) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String accessToken = root.path("access_token").asText();
            if (accessToken.isBlank()) {
                throw new IllegalStateException("Threads không trả về access token");
            }
            return new AccessToken(
                    accessToken,
                    root.path("user_id").asText(""),
                    root.path("expires_in").asLong(defaultExpiresIn)
            );
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Threads trả về token không hợp lệ", exception);
        }
    }

    public record AccessToken(String accessToken, String userId, long expiresInSeconds) {
    }

    public record ThreadsProfile(String id, String username) {
    }

    private record ContainerStatus(String code, String description) {
    }
}
