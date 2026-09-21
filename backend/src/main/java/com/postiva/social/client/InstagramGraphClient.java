package com.postiva.social.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
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
public class InstagramGraphClient {
    private static final int STATUS_ATTEMPTS = 30;
    private static final long STATUS_DELAY_MILLIS = 2_000;

    private final WebClient oauthClient;
    private final WebClient instagramClient;
    private final WebClient facebookClient;
    private final ObjectMapper objectMapper;

    public InstagramGraphClient(WebClient.Builder builder,
                                ObjectMapper objectMapper,
                                @Value("${postiva.instagram.api-version}") String apiVersion) {
        this.oauthClient = builder.clone().build();
        this.instagramClient = builder.clone()
                .baseUrl("https://graph.instagram.com/" + apiVersion)
                .build();
        this.facebookClient = builder.clone()
                .baseUrl("https://graph.facebook.com/" + apiVersion)
                .build();
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
                .uri("https://api.instagram.com/oauth/access_token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(String.class)
                .block();
        return parseToken(body, 0);
    }

    public OAuthToken exchangeLongLived(String appSecret, String accessToken) {
        String body = oauthClient.get()
                .uri(uri -> uri.scheme("https")
                        .host("graph.instagram.com")
                        .path("/access_token")
                        .queryParam("grant_type", "ig_exchange_token")
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
                .uri(uri -> uri.scheme("https")
                        .host("graph.instagram.com")
                        .path("/refresh_access_token")
                        .queryParam("grant_type", "ig_refresh_token")
                        .queryParam("access_token", accessToken)
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .map(responseBody -> new IllegalStateException(
                                metaErrorMessage("Instagram không thể refresh token", responseBody))))
                .bodyToMono(String.class)
                .block();
        return parseToken(body, 0);
    }

    public InstagramProfile getMe(String accessToken) {
        String body = instagramClient.get()
                .uri(uri -> uri.path("/me")
                        .queryParam("fields", "id,username,account_type")
                        .queryParam("access_token", accessToken)
                        .build())
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .map(responseBody -> new ResponseStatusException(
                                HttpStatus.BAD_GATEWAY,
                                metaErrorMessage("Instagram", responseBody)
                        )))
                .bodyToMono(String.class)
                .block();
        try {
            JsonNode root = objectMapper.readTree(body);
            return new InstagramProfile(
                    root.path("id").asText(),
                    root.path("username").asText(),
                    root.path("account_type").asText(""),
                    ApiMode.INSTAGRAM_LOGIN
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Instagram trả về hồ sơ không hợp lệ", exception);
        }
    }

    public InstagramProfile getProfile(String accountId, String accessToken) {
        RuntimeException businessError = null;
        if (accountId == null || accountId.isBlank()) {
            return getMe(accessToken);
        }
        try {
            return getBusinessProfile(accountId, accessToken);
        } catch (RuntimeException exception) {
            businessError = exception;
        }
        try {
            return getMe(accessToken);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Không thể kiểm tra Instagram. Nếu dùng Instagram Business qua Facebook Page, hãy dùng IG User ID và Page Access Token. "
                            + "Chi tiết: " + exceptionMessage(businessError)
            );
        }
    }

    public String createImageContainer(String accountId,
                                       String accessToken,
                                       String imageUrl,
                                       String caption,
                                       boolean carouselItem,
                                       ApiMode apiMode) {
        MultiValueMap<String, String> form = baseForm(accessToken);
        form.add("image_url", imageUrl);
        if (carouselItem) {
            form.add("is_carousel_item", "true");
        } else {
            addIfPresent(form, "caption", caption);
        }
        return postForId(client(apiMode), "/{accountId}/media", accountId, form, "tạo image container");
    }

    public String createVideoContainer(String accountId,
                                       String accessToken,
                                       String videoUrl,
                                       String caption,
                                       boolean carouselItem,
                                       ApiMode apiMode) {
        MultiValueMap<String, String> form = baseForm(accessToken);
        form.add("video_url", videoUrl);
        if (carouselItem) {
            form.add("media_type", "VIDEO");
            form.add("is_carousel_item", "true");
        } else {
            form.add("media_type", "REELS");
            form.add("share_to_feed", "true");
            addIfPresent(form, "caption", caption);
        }
        return postForId(client(apiMode), "/{accountId}/media", accountId, form, "tạo video container");
    }

    public String createCarouselContainer(String accountId,
                                          String accessToken,
                                          List<String> children,
                                          String caption,
                                          ApiMode apiMode) {
        MultiValueMap<String, String> form = baseForm(accessToken);
        form.add("media_type", "CAROUSEL");
        form.add("children", String.join(",", children));
        addIfPresent(form, "caption", caption);
        return postForId(client(apiMode), "/{accountId}/media", accountId, form, "tạo carousel container");
    }

    public String publishContainer(String accountId,
                                   String accessToken,
                                   String containerId,
                                   ApiMode apiMode) {
        MultiValueMap<String, String> form = baseForm(accessToken);
        form.add("creation_id", containerId);
        return postForId(client(apiMode), "/{accountId}/media_publish", accountId, form, "publish nội dung");
    }

    public void awaitContainerReady(String containerId, String accessToken, ApiMode apiMode) {
        for (int attempt = 0; attempt < STATUS_ATTEMPTS; attempt++) {
            ContainerStatus status = getContainerStatus(containerId, accessToken, apiMode);
            if ("FINISHED".equals(status.code()) || "PUBLISHED".equals(status.code())) {
                return;
            }
            if ("ERROR".equals(status.code()) || "EXPIRED".equals(status.code())) {
                throw new IllegalStateException("Instagram xử lý media thất bại: " + status.description());
            }
            pauseBeforeRetry();
        }
        throw new IllegalStateException("Instagram xử lý media quá lâu. Hãy thử đăng lại sau.");
    }

    private ContainerStatus getContainerStatus(String containerId, String accessToken, ApiMode apiMode) {
        String body = client(apiMode).get()
                .uri(uri -> uri.path("/{containerId}")
                        .queryParam("fields", "status_code,status")
                        .queryParam("access_token", accessToken)
                        .build(containerId))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .map(responseBody -> new IllegalStateException(metaErrorMessage("Instagram", responseBody))))
                .bodyToMono(String.class)
                .block();
        try {
            JsonNode root = objectMapper.readTree(body);
            return new ContainerStatus(
                    root.path("status_code").asText("").toUpperCase(),
                    root.path("status").asText("Đang xử lý media")
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Instagram trả về trạng thái media không hợp lệ", exception);
        }
    }

    private InstagramProfile getBusinessProfile(String accountId, String accessToken) {
        String body = facebookClient.get()
                .uri(uri -> uri.path("/{accountId}")
                        .queryParam("fields", "id,username")
                        .queryParam("access_token", accessToken)
                        .build(accountId))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .map(responseBody -> new ResponseStatusException(
                                HttpStatus.BAD_GATEWAY,
                                metaErrorMessage("Instagram Business", responseBody)
                        )))
                .bodyToMono(String.class)
                .block();
        try {
            JsonNode root = objectMapper.readTree(body);
            return new InstagramProfile(
                    root.path("id").asText(),
                    root.path("username").asText(),
                    "",
                    ApiMode.FACEBOOK_LOGIN
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Instagram trả về hồ sơ không hợp lệ", exception);
        }
    }

    private String postForId(WebClient targetClient,
                             String path,
                             String accountId,
                             MultiValueMap<String, String> form,
                             String action) {
        String body = targetClient.post()
                .uri(path, accountId)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                        .map(responseBody -> new IllegalStateException(
                                metaErrorMessage("Instagram không thể " + action, responseBody))))
                .bodyToMono(String.class)
                .block();
        try {
            String id = objectMapper.readTree(body).path("id").asText();
            if (id.isBlank()) {
                throw new IllegalStateException("Instagram không trả về ID");
            }
            return id;
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Instagram trả về dữ liệu publish không hợp lệ", exception);
        }
    }

    private MultiValueMap<String, String> baseForm(String accessToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("access_token", accessToken);
        return form;
    }

    private void addIfPresent(MultiValueMap<String, String> form, String key, String value) {
        if (value != null && !value.isBlank()) {
            form.add(key, value);
        }
    }

    private WebClient client(ApiMode apiMode) {
        return apiMode == ApiMode.INSTAGRAM_LOGIN ? instagramClient : facebookClient;
    }

    private void pauseBeforeRetry() {
        try {
            Thread.sleep(STATUS_DELAY_MILLIS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Đã dừng khi đang chờ Instagram xử lý media", exception);
        }
    }

    private OAuthToken parseToken(String body, long defaultExpiresIn) {
        try {
            JsonNode root = objectMapper.readTree(body);
            String accessToken = root.path("access_token").asText();
            if (accessToken.isBlank()) {
                throw new IllegalStateException("Instagram không trả về access token");
            }
            return new OAuthToken(
                    accessToken,
                    root.path("user_id").asText(""),
                    root.path("expires_in").asLong(defaultExpiresIn)
            );
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Instagram trả về token không hợp lệ", exception);
        }
    }

    private String metaErrorMessage(String platform, String body) {
        try {
            String message = objectMapper.readTree(body).path("error").path("message").asText();
            if (message != null && !message.isBlank()) {
                return platform + ": " + message;
            }
        } catch (Exception ignored) {
        }
        return platform + ". Kiểm tra access token, quyền publish và định dạng media.";
    }

    private String exceptionMessage(RuntimeException exception) {
        if (exception instanceof ResponseStatusException statusException
                && statusException.getReason() != null
                && !statusException.getReason().isBlank()) {
            return statusException.getReason();
        }
        String message = exception == null ? null : exception.getMessage();
        return message == null || message.isBlank()
                ? "Meta không chấp nhận token Instagram hiện tại."
                : message;
    }

    public enum ApiMode {
        FACEBOOK_LOGIN,
        INSTAGRAM_LOGIN
    }

    public record OAuthToken(String accessToken, String userId, long expiresInSeconds) {
    }

    public record InstagramProfile(String id, String username, String accountType, ApiMode apiMode) {
    }

    private record ContainerStatus(String code, String description) {
    }
}
