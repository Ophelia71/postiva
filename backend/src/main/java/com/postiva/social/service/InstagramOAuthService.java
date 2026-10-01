package com.postiva.social.service;

import com.postiva.auth.service.CurrentUserService;
import com.postiva.social.client.InstagramGraphClient;
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
public class InstagramOAuthService {
    private final String appId;
    private final String appSecret;
    private final String redirectUri;
    private final CurrentUserService currentUserService;
    private final MetaOAuthStateService stateService;
    private final InstagramGraphClient graphClient;
    private final SocialAccountService accountService;

    public InstagramOAuthService(
            @Value("${postiva.instagram.app-id:}") String appId,
            @Value("${postiva.instagram.app-secret:}") String appSecret,
            @Value("${postiva.instagram.redirect-uri}") String redirectUri,
            CurrentUserService currentUserService,
            MetaOAuthStateService stateService,
            InstagramGraphClient graphClient,
            SocialAccountService accountService
    ) {
        this.appId = appId;
        this.appSecret = appSecret;
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
        String url = "https://www.instagram.com/oauth/authorize"
                + "?client_id=" + encode(appId)
                + "&redirect_uri=" + encode(redirectUri)
                + "&response_type=code"
                + "&scope=" + encode("instagram_business_basic,instagram_business_content_publish")
                + "&enable_fb_login=0"
                + "&force_authentication=1"
                + "&state=" + encode(state);
        return new SocialConnectUrlResponse(url, "Đăng nhập Instagram để kết nối tài khoản Business");
    }

    public void complete(String code, String state) {
        UUID userId = stateService.consume(state);
        requireConfiguration();
        InstagramGraphClient.OAuthToken shortToken = graphClient.exchangeCode(appId, appSecret, redirectUri, code);
        InstagramGraphClient.OAuthToken token = graphClient.exchangeLongLived(appSecret, shortToken.accessToken());
        InstagramGraphClient.InstagramProfile profile = graphClient.getMe(token.accessToken());
        String accountId = profile.id().isBlank() ? shortToken.userId() : profile.id();
        LocalDateTime expiresAt = token.expiresInSeconds() <= 0
                ? null : LocalDateTime.now().plusSeconds(token.expiresInSeconds());
        accountService.saveInstagramAccount(
                userId, accountId, profile.username(), profile.accountType(), profile.apiMode(), token.accessToken(), expiresAt);
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
            return "Chưa cấu hình INSTAGRAM_APP_ID";
        }
        if (!appId.matches("[0-9]+")) {
            return "INSTAGRAM_APP_ID phải là App ID dạng số hợp lệ";
        }
        if (appSecret == null || appSecret.isBlank()) {
            return "Chưa cấu hình INSTAGRAM_APP_SECRET";
        }
        if (appSecret.equalsIgnoreCase("your_instagram_app_secret")) {
            return "INSTAGRAM_APP_SECRET vẫn là giá trị placeholder";
        }
        if (redirectUri == null || redirectUri.isBlank()) {
            return "Chưa cấu hình INSTAGRAM_REDIRECT_URI";
        }
        return null;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
