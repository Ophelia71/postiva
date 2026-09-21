package com.postiva.social.service;

import com.postiva.auth.service.CurrentUserService;
import com.postiva.social.client.ThreadsGraphClient;
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
public class ThreadsOAuthService {
    private final String appId;
    private final String appSecret;
    private final String redirectUri;
    private final CurrentUserService currentUserService;
    private final MetaOAuthStateService stateService;
    private final ThreadsGraphClient graphClient;
    private final SocialAccountService accountService;

    public ThreadsOAuthService(
            @Value("${postiva.threads.app-id:}") String appId,
            @Value("${postiva.threads.app-secret:}") String appSecret,
            @Value("${postiva.threads.redirect-uri}") String redirectUri,
            CurrentUserService currentUserService,
            MetaOAuthStateService stateService,
            ThreadsGraphClient graphClient,
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
            return new SocialConnectUrlResponse(null, "Chưa cấu hình THREADS_APP_ID");
        }
        String state = stateService.issue(currentUserService.requireCurrentUserId());
        String url = "https://threads.net/oauth/authorize"
                + "?client_id=" + encode(appId)
                + "&redirect_uri=" + encode(redirectUri)
                + "&response_type=code"
                + "&scope=" + encode("threads_basic,threads_content_publish")
                + "&state=" + encode(state);
        return new SocialConnectUrlResponse(url, "Đăng nhập Threads để kết nối tài khoản");
    }

    public void complete(String code, String state) {
        requireConfiguration();
        UUID userId = stateService.consume(state);
        ThreadsGraphClient.OAuthToken shortToken = graphClient.exchangeCode(appId, appSecret, redirectUri, code);
        ThreadsGraphClient.OAuthToken token = graphClient.exchangeLongLived(appSecret, shortToken.accessToken());
        ThreadsGraphClient.ThreadsProfile profile = graphClient.getMe(token.accessToken());
        String accountId = profile.id().isBlank() ? shortToken.userId() : profile.id();
        LocalDateTime expiresAt = token.expiresInSeconds() <= 0
                ? null : LocalDateTime.now().plusSeconds(token.expiresInSeconds());
        accountService.saveThreadsAccount(userId, accountId, profile.username(), token.accessToken(), expiresAt);
    }

    private void requireConfiguration() {
        if (appId == null || appId.isBlank() || appSecret == null || appSecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Chưa cấu hình Threads App ID/App Secret");
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
