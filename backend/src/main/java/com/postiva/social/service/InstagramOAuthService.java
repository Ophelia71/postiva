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
        if (appId == null || appId.isBlank()) {
            return new SocialConnectUrlResponse(null, "Chưa cấu hình INSTAGRAM_APP_ID");
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
        requireConfiguration();
        UUID userId = stateService.consume(state);
        InstagramGraphClient.OAuthToken shortToken = graphClient.exchangeCode(appId, appSecret, redirectUri, code);
        InstagramGraphClient.OAuthToken token = graphClient.exchangeLongLived(appSecret, shortToken.accessToken());
        InstagramGraphClient.InstagramProfile profile = graphClient.getMe(token.accessToken());
        String accountId = profile.id().isBlank() ? shortToken.userId() : profile.id();
        LocalDateTime expiresAt = token.expiresInSeconds() <= 0
                ? null : LocalDateTime.now().plusSeconds(token.expiresInSeconds());
        accountService.saveInstagramAccount(
                userId, accountId, profile.username(), profile.accountType(), profile.apiMode(), token.accessToken(), expiresAt);
    }

    private void requireConfiguration() {
        if (appId == null || appId.isBlank() || appSecret == null || appSecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Chưa cấu hình Instagram App ID/App Secret");
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
