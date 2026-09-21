package com.postiva.social.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Component
public class FacebookGraphClient {
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public FacebookGraphClient(WebClient.Builder builder,
                               ObjectMapper objectMapper,
                               @Value("${postiva.meta.api-version}") String apiVersion) {
        this.webClient = builder.baseUrl("https://graph.facebook.com/" + apiVersion).build();
        this.objectMapper = objectMapper;
    }

    public OAuthToken exchangeCode(String appId, String appSecret, String redirectUri, String code) {
        String body = webClient.get()
                .uri(uri -> uri.path("/oauth/access_token")
                        .queryParam("client_id", appId)
                        .queryParam("client_secret", appSecret)
                        .queryParam("redirect_uri", redirectUri)
                        .queryParam("code", code)
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .map(responseBody -> new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                                metaErrorMessage("đổi authorization code", responseBody))))
                .bodyToMono(String.class)
                .block();
        return parseToken(body);
    }

    public OAuthToken exchangeLongLivedUserToken(String appId, String appSecret, String shortLivedToken) {
        String body = webClient.get()
                .uri(uri -> uri.path("/oauth/access_token")
                        .queryParam("grant_type", "fb_exchange_token")
                        .queryParam("client_id", appId)
                        .queryParam("client_secret", appSecret)
                        .queryParam("fb_exchange_token", shortLivedToken)
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .map(responseBody -> new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                                metaErrorMessage("đổi long-lived token", responseBody))))
                .bodyToMono(String.class)
                .block();
        return parseToken(body);
    }

    public List<FacebookPage> getManagedPages(String userAccessToken) {
        String body = webClient.get()
                .uri(uri -> uri.path("/me/accounts")
                        .queryParam("fields", "id,name,access_token,tasks")
                        .queryParam("limit", 100)
                        .queryParam("access_token", userAccessToken)
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .map(responseBody -> new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                                metaErrorMessage("đọc danh sách Facebook Page", responseBody))))
                .bodyToMono(String.class)
                .block();
        try {
            List<FacebookPage> pages = new ArrayList<>();
            for (JsonNode node : objectMapper.readTree(body).path("data")) {
                pages.add(new FacebookPage(
                        node.path("id").asText(),
                        node.path("name").asText(),
                        node.path("access_token").asText()
                ));
            }
            return pages;
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể đọc danh sách Facebook Page", exception);
        }
    }

    public FacebookPage getPage(String pageId, String pageAccessToken) {
        String body = webClient.get()
                .uri(uri -> uri.path("/{pageId}")
                        .queryParam("fields", "id,name")
                        .queryParam("access_token", pageAccessToken)
                        .build(pageId))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .map(responseBody -> new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                                metaErrorMessage("đọc thông tin Facebook Page", responseBody))))
                .bodyToMono(String.class)
                .block();
        try {
            JsonNode root = objectMapper.readTree(body);
            String id = root.path("id").asText();
            if (id.isBlank()) {
                throw new IllegalStateException("Meta không trả về Facebook Page ID");
            }
            return new FacebookPage(id, root.path("name").asText(""), pageAccessToken);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Meta trả về thông tin Facebook Page không hợp lệ", exception);
        }
    }

    public String getCurrentUserId(String userAccessToken) {
        String body = webClient.get()
                .uri(uri -> uri.path("/me")
                        .queryParam("fields", "id")
                        .queryParam("access_token", userAccessToken)
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .map(responseBody -> new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                                metaErrorMessage("đọc Meta user ID", responseBody))))
                .bodyToMono(String.class)
                .block();
        try {
            String userId = objectMapper.readTree(body).path("id").asText();
            if (userId.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Meta không trả về user ID");
            }
            return userId;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Meta trả về user ID không hợp lệ", exception);
        }
    }

    public String publishText(String pageId, String accessToken, String message) {
        return publishText(pageId, accessToken, message, true);
    }

    /**
     * Creates a Page text post without publishing it to the Page timeline.
     * This is used only for the connection test so customers cannot see it.
     */
    public String publishUnpublishedText(String pageId, String accessToken, String message) {
        return publishText(pageId, accessToken, message, false);
    }

    private String publishText(String pageId, String accessToken, String message, boolean published) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("message", message);
        form.add("access_token", accessToken);
        form.add("published", Boolean.toString(published));
        return extractObjectId(webClient.post()
                .uri("/{pageId}/feed", pageId)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(String.class)
                .block());
    }

    public String publishPhoto(String pageId, String accessToken, String caption, Resource image) {
        MultipartBodyBuilder parts = new MultipartBodyBuilder();
        parts.part("source", image);
        parts.part("caption", caption);
        parts.part("access_token", accessToken);
        return extractObjectId(webClient.post()
                .uri("/{pageId}/photos", pageId)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(parts.build()))
                .retrieve()
                .bodyToMono(String.class)
                .block());
    }

    public String publishPhotos(String pageId, String accessToken, String message, List<Resource> images) {
        List<String> photoIds = images.stream()
                .map(image -> uploadUnpublishedPhoto(pageId, accessToken, image))
                .toList();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("message", message);
        form.add("access_token", accessToken);
        for (int index = 0; index < photoIds.size(); index++) {
            form.add("attached_media[" + index + "]", "{\"media_fbid\":\"" + photoIds.get(index) + "\"}");
        }
        return extractObjectId(webClient.post()
                .uri("/{pageId}/feed", pageId)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(String.class)
                .block());
    }

    private String uploadUnpublishedPhoto(String pageId, String accessToken, Resource image) {
        MultipartBodyBuilder parts = new MultipartBodyBuilder();
        parts.part("source", image);
        parts.part("published", "false");
        parts.part("access_token", accessToken);
        return extractObjectId(webClient.post()
                .uri("/{pageId}/photos", pageId)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(parts.build()))
                .retrieve()
                .bodyToMono(String.class)
                .block());
    }

    public String publishPhotoUrl(String pageId, String accessToken, String caption, String imageUrl) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("url", imageUrl);
        form.add("caption", caption);
        form.add("access_token", accessToken);
        return extractObjectId(webClient.post()
                .uri("/{pageId}/photos", pageId)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(String.class)
                .block());
    }

    public String publishVideo(String pageId, String accessToken, String description, Resource video) {
        MultipartBodyBuilder parts = new MultipartBodyBuilder();
        parts.part("source", video);
        parts.part("description", description);
        parts.part("access_token", accessToken);
        return extractObjectId(webClient.post()
                .uri("/{pageId}/videos", pageId)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(parts.build()))
                .retrieve()
                .bodyToMono(String.class)
                .block());
    }

    public String publishVideoUrl(String pageId, String accessToken, String description, String videoUrl) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("file_url", videoUrl);
        form.add("description", description);
        form.add("access_token", accessToken);
        return extractObjectId(webClient.post()
                .uri("/{pageId}/videos", pageId)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(String.class)
                .block());
    }

    public void updatePostMessage(String postId, String accessToken, String message) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("message", message);
        form.add("access_token", accessToken);
        webClient.post()
                .uri("/{postId}", postId)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public void deletePost(String postId, String accessToken) {
        webClient.delete()
                .uri(uri -> uri.path("/{postId}")
                        .queryParam("access_token", accessToken)
                        .build(postId))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .map(body -> new IllegalStateException("Meta không thể xóa bài viết: " + body)))
                .bodyToMono(String.class)
                .block();
    }

    public List<FacebookPagePost> getPagePosts(String pageId, String accessToken) {
        String body = webClient.get()
                .uri(uri -> uri.path("/{pageId}/posts")
                        .queryParam("fields", "id,message,created_time,permalink_url")
                        .queryParam("limit", 100)
                        .queryParam("access_token", accessToken)
                        .build(pageId))
                .retrieve()
                .bodyToMono(String.class)
                .block();
        try {
            List<FacebookPagePost> posts = new ArrayList<>();
            for (JsonNode node : objectMapper.readTree(body).path("data")) {
                String id = node.path("id").asText();
                if (!id.isBlank()) {
                    posts.add(new FacebookPagePost(
                            id,
                            node.path("message").asText(""),
                            parseDate(node.path("created_time").asText()),
                            node.path("permalink_url").asText("")
                    ));
                }
            }
            return posts;
        } catch (Exception exception) {
            throw new IllegalStateException("Meta trả về dữ liệu bài đăng không hợp lệ", exception);
        }
    }

    private String extractObjectId(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String id = root.path("post_id").asText();
            return id.isBlank() ? root.path("id").asText() : id;
        } catch (Exception exception) {
            throw new IllegalStateException("Meta trả về dữ liệu đăng bài không hợp lệ", exception);
        }
    }

    private OAuthToken parseToken(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String accessToken = root.path("access_token").asText();
            if (accessToken.isBlank()) {
                throw new IllegalStateException("Meta không trả về access token");
            }
            return new OAuthToken(accessToken, root.path("expires_in").asLong(0));
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Meta trả về token không hợp lệ", exception);
        }
    }

    private String metaErrorMessage(String action, String body) {
        try {
            String message = objectMapper.readTree(body).path("error").path("message").asText();
            if (!message.isBlank()) {
                return "Meta không thể " + action + ": " + message;
            }
        } catch (Exception ignored) {
        }
        return "Meta không thể " + action + ". Hãy kiểm tra access token và quyền ứng dụng.";
    }

    private LocalDateTime parseDate(String value) {
        if (value == null || value.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        } catch (RuntimeException exception) {
            return LocalDateTime.now();
        }
    }

    public record OAuthToken(String accessToken, long expiresInSeconds) {
    }

    public record FacebookPage(String id, String name, String accessToken) {
    }

    public record FacebookPagePost(String id, String message, LocalDateTime createdAt, String permalinkUrl) {
    }
}
