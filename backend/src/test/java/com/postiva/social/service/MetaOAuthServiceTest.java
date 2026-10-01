package com.postiva.social.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.postiva.auth.service.CurrentUserService;
import com.postiva.social.client.FacebookGraphClient;
import com.postiva.social.dto.SocialConnectUrlResponse;
import com.postiva.social.entity.SocialAccount;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetaOAuthServiceTest {
    @Test
    void connectUrlRejectsPlaceholderAppId() {
        MetaOAuthService service = new MetaOAuthService("your_meta_app_id", "app-secret", "v26.0",
                "http://localhost:8080/api/social/meta/callback", null, null, null, null);

        SocialConnectUrlResponse response = service.createConnectUrl();

        assertEquals(null, response.url());
        assertTrue(response.note().contains("META_APP_ID"));
    }

    @Test
    void connectUrlUsesConfiguredAppIdRedirectAndFacebookScopes() {
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        when(currentUserService.requireCurrentUserId()).thenReturn(UUID.randomUUID());
        MetaOAuthService service = new MetaOAuthService("123456789", "app-secret", "v26.0",
                "http://localhost:8080/api/social/meta/callback", currentUserService,
                new MetaOAuthStateService(), null, null);

        String url = service.createConnectUrl().url();

        assertTrue(url.contains("client_id=123456789"));
        assertTrue(url.contains("redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fapi%2Fsocial%2Fmeta%2Fcallback"));
        assertTrue(url.contains("scope=pages_show_list%2Cpages_read_engagement%2Cpages_manage_posts"));
    }

    @Test
    void exchangesLongLivedTokenBeforeLoadingAndSavingPages() {
        UUID userId = UUID.randomUUID();
        MetaOAuthStateService stateService = new MetaOAuthStateService();
        String state = stateService.issue(userId);
        StubFacebookGraphClient graphClient = new StubFacebookGraphClient();
        CapturingAccountService accountService = new CapturingAccountService();
        MetaOAuthService service = new MetaOAuthService(
                "123456789",
                "app-secret",
                "v26.0",
                "https://example.test/api/social/meta/callback",
                null,
                stateService,
                graphClient,
                accountService
        );

        int pageCount = service.complete("code", state);

        assertEquals(1, pageCount);
        assertEquals("short-token", graphClient.longLivedExchangeInput);
        assertEquals("long-token", graphClient.managedPagesToken);
        assertEquals("meta-user-1", accountService.oauthUserId);
        assertEquals(userId, accountService.userId);
        assertEquals("page-1", accountService.pageId);
        assertEquals("Postiva", accountService.pageName);
        assertEquals("page-token", accountService.pageAccessToken);
        assertTrue(accountService.expiresAt.isAfter(LocalDateTime.now().plusDays(50)));
    }

    @Test
    void callbackReportsOnlyPagesWithTokens() {
        MetaOAuthStateService stateService = new MetaOAuthStateService();
        StubFacebookGraphClient graphClient = new StubFacebookGraphClient(List.of(
                new FacebookGraphClient.FacebookPage("page-1", "Postiva", "page-token"),
                new FacebookGraphClient.FacebookPage("page-2", "Unavailable", "")));
        CapturingAccountService accountService = new CapturingAccountService();
        MetaOAuthService service = new MetaOAuthService("123456789", "app-secret", "v26.0",
                "https://example.test/api/social/meta/callback", null, stateService, graphClient, accountService);

        assertEquals(1, service.complete("code", stateService.issue(UUID.randomUUID())));
        assertEquals(1, accountService.savedCount);
    }

    @Test
    void callbackDoesNotReportSuccessWhenNoPageCanBeSaved() {
        MetaOAuthStateService stateService = new MetaOAuthStateService();
        CapturingAccountService accountService = new CapturingAccountService();
        MetaOAuthService service = new MetaOAuthService("123456789", "app-secret", "v26.0",
                "https://example.test/api/social/meta/callback", null, stateService,
                new StubFacebookGraphClient(List.of(new FacebookGraphClient.FacebookPage("page-1", "Postiva", ""))),
                accountService);

        assertThrows(MetaOAuthService.NoFacebookPagesException.class,
                () -> service.complete("code", stateService.issue(UUID.randomUUID())));
        assertEquals(0, accountService.savedCount);
    }

    private static final class StubFacebookGraphClient extends FacebookGraphClient {
        private final List<FacebookPage> pages;
        private String longLivedExchangeInput;
        private String managedPagesToken;

        private StubFacebookGraphClient() {
            this(List.of(new FacebookPage("page-1", "Postiva", "page-token")));
        }

        private StubFacebookGraphClient(List<FacebookPage> pages) {
            super(WebClient.builder(), new ObjectMapper(), "v26.0");
            this.pages = pages;
        }

        @Override
        public OAuthToken exchangeCode(String appId, String appSecret, String redirectUri, String code) {
            return new OAuthToken("short-token", 3_600);
        }

        @Override
        public OAuthToken exchangeLongLivedUserToken(String appId, String appSecret, String shortLivedToken) {
            this.longLivedExchangeInput = shortLivedToken;
            return new OAuthToken("long-token", 5_184_000);
        }

        @Override
        public List<FacebookPage> getManagedPages(String userAccessToken) {
            this.managedPagesToken = userAccessToken;
            return pages;
        }

        @Override
        public String getCurrentUserId(String userAccessToken) {
            assertEquals("long-token", userAccessToken);
            return "meta-user-1";
        }
    }

    private static final class CapturingAccountService extends SocialAccountService {
        private int savedCount;
        private UUID userId;
        private String pageId;
        private String pageName;
        private String pageAccessToken;
        private LocalDateTime expiresAt;
        private String oauthUserId;

        private CapturingAccountService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public SocialAccount saveFacebookPage(UUID userId,
                                              String pageId,
                                              String pageName,
                                              String pageAccessToken,
                                              LocalDateTime expiresAt,
                                              String oauthUserId) {
            this.savedCount++;
            this.userId = userId;
            this.pageId = pageId;
            this.pageName = pageName;
            this.pageAccessToken = pageAccessToken;
            this.expiresAt = expiresAt;
            this.oauthUserId = oauthUserId;
            return new SocialAccount();
        }
    }
}
