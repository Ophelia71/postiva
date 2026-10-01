package com.postiva.social.service;

import com.postiva.auth.service.CurrentUserService;
import com.postiva.social.client.FacebookGraphClient;
import com.postiva.social.dto.SocialConnectUrlResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class MetaOAuthService {
    private final String appId;
    private final String appSecret;
    private final String apiVersion;
    private final String redirectUri;
    private final CurrentUserService currentUserService;
    private final MetaOAuthStateService stateService;
    private final FacebookGraphClient graphClient;
    private final SocialAccountService accountService;

    public MetaOAuthService(
            @Value("${postiva.meta.app-id:}") String appId,
            @Value("${postiva.meta.app-secret:}") String appSecret,
            @Value("${postiva.meta.api-version}") String apiVersion,
            @Value("${postiva.meta.redirect-uri}") String redirectUri,
            CurrentUserService currentUserService,
            MetaOAuthStateService stateService,
            FacebookGraphClient graphClient,
            SocialAccountService accountService
    ) {
        this.appId = appId;
        this.appSecret = appSecret;
        this.apiVersion = apiVersion;
        this.redirectUri = redirectUri;
        this.currentUserService = currentUserService;
        this.stateService = stateService;
        this.graphClient = graphClient;
        this.accountService = accountService;
    }

    public SocialConnectUrlResponse createConnectUrl() {
        String configurationError = configurationError();
        if (configurationError != null) {
            return new SocialConnectUrlResponse(null, configurationError);
        }
        String state = stateService.issue(currentUserService.requireCurrentUserId());
        String url = "https://www.facebook.com/" + apiVersion + "/dialog/oauth"
                + "?client_id=" + encode(appId)
                + "&redirect_uri=" + encode(redirectUri)
                + "&state=" + encode(state)
                + "&scope=" + encode("pages_show_list,pages_read_engagement,pages_manage_posts");
        return new SocialConnectUrlResponse(url, "Đăng nhập Meta và chọn Facebook Page muốn kết nối");
    }

    public int complete(String code, String state) {
        UUID userId = stateService.consume(state);
        requireConfiguration();
        FacebookGraphClient.OAuthToken shortToken = graphClient.exchangeCode(appId, appSecret, redirectUri, code);
        FacebookGraphClient.OAuthToken token = graphClient.exchangeLongLivedUserToken(
                appId, appSecret, shortToken.accessToken());
        if (token.accessToken() == null || token.accessToken().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Meta không trả về access token");
        }
        LocalDateTime expiresAt = token.expiresInSeconds() <= 0
                ? null : LocalDateTime.now().plusSeconds(token.expiresInSeconds());
        String oauthUserId = graphClient.getCurrentUserId(token.accessToken());
        var pages = graphClient.getManagedPages(token.accessToken());
        int savedPages = 0;
        for (FacebookGraphClient.FacebookPage page : pages) {
            if (page.accessToken() != null && !page.accessToken().isBlank()) {
                accountService.saveFacebookPage(
                        userId, page.id(), page.name(), page.accessToken(), expiresAt, oauthUserId);
                savedPages++;
            }
        }
        if (savedPages == 0) {
            throw new NoFacebookPagesException();
        }
        return savedPages;
    }

    public void consumeFailedCallbackState(String state) {
        stateService.consume(state);
    }

    private void requireConfiguration() {
        String configurationError = configurationError();
        if (configurationError != null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, configurationError);
        }
    }

    private String configurationError() {
        if (appId == null || appId.isBlank()) {
            return "Chưa cấu hình META_APP_ID";
        }
        if (!appId.matches("[0-9]+")) {
            return "META_APP_ID phải là Facebook App ID dạng số hợp lệ";
        }
        if (appSecret == null || appSecret.isBlank()) {
            return "Chưa cấu hình META_APP_SECRET";
        }
        if (appSecret.equalsIgnoreCase("your_meta_app_secret")) {
            return "META_APP_SECRET vẫn là giá trị placeholder";
        }
        if (redirectUri == null || redirectUri.isBlank()) {
            return "Chưa cấu hình META_REDIRECT_URI";
        }
        return null;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public static final class NoFacebookPagesException extends RuntimeException {
        public NoFacebookPagesException() {
            super("Meta không trả về Facebook Page có access token");
        }
    }
}
