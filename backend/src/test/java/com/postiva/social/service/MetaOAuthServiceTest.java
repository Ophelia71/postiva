package com.postiva.social.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.postiva.social.client.FacebookGraphClient;
import com.postiva.social.entity.SocialAccount;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetaOAuthServiceTest {
    @Test
    void exchangesLongLivedTokenBeforeLoadingAndSavingPages() {
        UUID userId = UUID.randomUUID();
        MetaOAuthStateService stateService = new MetaOAuthStateService();
        String state = stateService.issue(userId);
        StubFacebookGraphClient graphClient = new StubFacebookGraphClient();
        CapturingAccountService accountService = new CapturingAccountService();
        MetaOAuthService service = new MetaOAuthService(
                "app-id",
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

    private static final class StubFacebookGraphClient extends FacebookGraphClient {
        private String longLivedExchangeInput;
        private String managedPagesToken;

        private StubFacebookGraphClient() {
            super(WebClient.builder(), new ObjectMapper(), "v26.0");
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
            return List.of(new FacebookPage("page-1", "Postiva", "page-token"));
        }

        @Override
        public String getCurrentUserId(String userAccessToken) {
            assertEquals("long-token", userAccessToken);
            return "meta-user-1";
        }
    }

    private static final class CapturingAccountService extends SocialAccountService {
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
