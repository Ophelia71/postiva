package com.postiva.social.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.postiva.common.Platform;
import com.postiva.social.insights.PostMetrics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
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
import java.time.Instant;
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

    public FacebookPage getPage(String pageId, String pageAccessToken) {
        String body = webClient.get()
                .uri(uri -> uri.path("/{pageId}")
                        .queryParam("fields", "id,name")
                        .queryParam("access_token", pageAccessToken)
                        .build(pageId))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .map(responseBody -> new MetaApiException(response.statusCode().value(),
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
                        .map(body -> new MetaApiException(response.statusCode().value(),
                                "Meta không thể xóa bài viết: " + body)))
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

    /**
     * Recent Page posts with reaction, comment and share counts. Needs pages_read_engagement; views are not
     * requested because they need read_insights, which most manually connected tokens do not have.
     */
    public List<PostMetrics> getPagePostMetrics(String pageId, String accessToken, int limit) {
        String body = webClient.get()
                .uri(uri -> uri.path("/{pageId}/posts")
                        // The braces in attachments{media_type} are Graph field expansion, not URI template
                        // variables, so the list goes in as a value rather than as part of the template.
                        .queryParam("fields", "{fields}")
                        .queryParam("limit", limit)
                        .queryParam("access_token", accessToken)
                        .build(pageId, "id,message,created_time,permalink_url,shares,attachments{media_type},"
                                + "reactions.summary(total_count).limit(0),comments.summary(total_count).limit(0)"))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .map(responseBody -> new MetaApiException(response.statusCode().value(),
                                metaErrorMessage("đọc tương tác bài đăng Facebook", responseBody))))
                .bodyToMono(String.class)
                .block();
        try {
            List<PostMetrics> posts = new ArrayList<>();
            for (JsonNode node : objectMapper.readTree(body).path("data")) {
                String id = node.path("id").asText();
                if (id.isBlank()) {
                    continue;
                }
                JsonNode attachment = node.path("attachments").path("data").path(0);
                posts.add(new PostMetrics(
                        id,
                        Platform.FACEBOOK,
                        parseDate(node.path("created_time").asText()),
                        node.path("message").asText(""),
                        attachment.isMissingNode() ? "TEXT" : attachment.path("media_type").asText("TEXT").toUpperCase(),
                        countOrNull(node.path("reactions").path("summary").path("total_count")),
                        countOrNull(node.path("comments").path("summary").path("total_count")),
                        // Meta omits "shares" entirely when a post has never been shared.
                        node.path("shares").path("count").canConvertToInt() ? node.path("shares").path("count").asInt() : 0,
                        null,
                        node.path("permalink_url").asText(null)
                ));
            }
            return posts;
        } catch (Exception exception) {
            throw new IllegalStateException("Meta trả về dữ liệu tương tác không hợp lệ", exception);
        }
    }

    private static Integer countOrNull(JsonNode node) {
        return node.canConvertToInt() ? node.asInt() : null;
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

    static Instant parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (RuntimeException exception) {
            try {
                return OffsetDateTime.parse(value,
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")).toInstant();
            } catch (RuntimeException invalidDate) {
                return null;
            }
        }
    }

    public record FacebookPage(String id, String name, String accessToken) {
    }

    public record FacebookPagePost(String id, String message, Instant createdAt, String permalinkUrl) {
    }
}
