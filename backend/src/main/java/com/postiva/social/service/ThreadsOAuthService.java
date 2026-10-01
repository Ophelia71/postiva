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
        String configurationError = configurationError();
        if (configurationError != null) {
            return new SocialConnectUrlResponse(null, configurationError);
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
        UUID userId = stateService.consume(state);
        requireConfiguration();
        ThreadsGraphClient.OAuthToken shortToken = graphClient.exchangeCode(appId, appSecret, redirectUri, code);
        ThreadsGraphClient.OAuthToken token = graphClient.exchangeLongLived(appSecret, shortToken.accessToken());
        ThreadsGraphClient.ThreadsProfile profile = graphClient.getMe(token.accessToken());
        String accountId = profile.id().isBlank() ? shortToken.userId() : profile.id();
        LocalDateTime expiresAt = token.expiresInSeconds() <= 0
                ? null : LocalDateTime.now().plusSeconds(token.expiresInSeconds());
        accountService.saveThreadsAccount(userId, accountId, profile.username(), token.accessToken(), expiresAt);
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
            return "Chưa cấu hình THREADS_APP_ID";
        }
        if (!appId.matches("[0-9]+")) {
            return "THREADS_APP_ID phải là App ID dạng số hợp lệ";
        }
        if (appSecret == null || appSecret.isBlank()) {
            return "Chưa cấu hình THREADS_APP_SECRET";
        }
        if (appSecret.equalsIgnoreCase("your_threads_app_secret")) {
            return "THREADS_APP_SECRET vẫn là giá trị placeholder";
        }
        if (redirectUri == null || redirectUri.isBlank()) {
            return "Chưa cấu hình THREADS_REDIRECT_URI";
        }
        return null;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
