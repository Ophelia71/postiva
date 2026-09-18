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
        if (appId == null || appId.isBlank()) {
            return new SocialConnectUrlResponse(null, "Chưa cấu hình META_APP_ID");
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
        requireConfiguration();
        UUID userId = stateService.consume(state);
        FacebookGraphClient.OAuthToken token = graphClient.exchangeCode(appId, appSecret, redirectUri, code);
        if (token.accessToken() == null || token.accessToken().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Meta không trả về access token");
        }
        LocalDateTime expiresAt = token.expiresInSeconds() <= 0
                ? null : LocalDateTime.now().plusSeconds(token.expiresInSeconds());
        var pages = graphClient.getManagedPages(token.accessToken());
        for (FacebookGraphClient.FacebookPage page : pages) {
            if (page.accessToken() != null && !page.accessToken().isBlank()) {
                accountService.saveFacebookPage(userId, page.id(), page.name(), page.accessToken(), expiresAt);
            }
        }
        return pages.size();
    }

    private void requireConfiguration() {
        if (appId == null || appId.isBlank() || appSecret == null || appSecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Chưa cấu hình Meta App ID/App Secret");
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
