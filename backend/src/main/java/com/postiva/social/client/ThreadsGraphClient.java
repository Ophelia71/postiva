package com.postiva.social.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Component
public class ThreadsGraphClient {
    private static final int STATUS_ATTEMPTS = 30;
    private static final long STATUS_DELAY_MILLIS = 2_000;

    private final WebClient oauthClient;
    private final WebClient apiClient;
    private final ObjectMapper objectMapper;

    public ThreadsGraphClient(WebClient.Builder builder, ObjectMapper objectMapper) {
        this.oauthClient = builder.clone().baseUrl("https://graph.threads.net").build();
        this.apiClient = builder.clone().baseUrl("https://graph.threads.net/v1.0").build();
        this.objectMapper = objectMapper;
    }

    public OAuthToken exchangeCode(String appId, String appSecret, String redirectUri, String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", appId);
        form.add("client_secret", appSecret);
        form.add("grant_type", "authorization_code");
        form.add("redirect_uri", redirectUri);
        form.add("code", code);
        String body = oauthClient.post()
                .uri("/oauth/access_token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parseToken(body, 0);
    }

    public OAuthToken exchangeLongLived(String appSecret, String accessToken) {
        String body = oauthClient.get()
                .uri(uri -> uri.path("/access_token")
                        .queryParam("grant_type", "th_exchange_token")
                        .queryParam("client_secret", appSecret)
                        .queryParam("access_token", accessToken)
                        .build())
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parseToken(body, 0);
    }

    public OAuthToken refreshLongLived(String accessToken) {
        String body = oauthClient.get()
                .uri(uri -> uri.path("/refresh_access_token")
                        .queryParam("grant_type", "th_refresh_token")
                        .queryParam("access_token", accessToken)
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .map(responseBody -> new ResponseStatusException(
                                HttpStatus.BAD_GATEWAY, metaErrorMessage(responseBody))))
                .bodyToMono(String.class)
                .block();
        return parseToken(body, 0);
    }

    public ThreadsProfile getMe(String accessToken) {
        String body = apiClient.get()
                .uri(uri -> uri.path("/me")
                        .queryParam("fields", "id,username")
                        .queryParam("access_token", accessToken)
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .map(responseBody -> new ResponseStatusException(
                                HttpStatus.BAD_GATEWAY, metaErrorMessage(responseBody))))
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
                        .map(responseBody -> new IllegalStateException(metaErrorMessage(responseBody))))
                .bodyToMono(String.class)
                .block();
        return extractId(body, "Threads không trả về ID bài đăng");
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
                        .map(responseBody -> new IllegalStateException(metaErrorMessage(responseBody))))
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
                        .map(responseBody -> new IllegalStateException(metaErrorMessage(responseBody))))
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

    private OAuthToken parseToken(String body, long defaultExpiresIn) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String accessToken = root.path("access_token").asText();
            if (accessToken.isBlank()) {
                throw new IllegalStateException("Threads không trả về access token");
            }
            return new OAuthToken(
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

    public record OAuthToken(String accessToken, String userId, long expiresInSeconds) {
    }

    public record ThreadsProfile(String id, String username) {
    }

    private record ContainerStatus(String code, String description) {
    }
}
