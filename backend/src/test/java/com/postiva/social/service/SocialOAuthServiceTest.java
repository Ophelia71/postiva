package com.postiva.social.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.postiva.auth.service.CurrentUserService;
import com.postiva.social.client.InstagramGraphClient;
import com.postiva.social.client.ThreadsGraphClient;
import com.postiva.social.dto.SocialConnectUrlResponse;
import com.postiva.social.entity.SocialAccount;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SocialOAuthServiceTest {
    @Test
    void connectUrlsRejectPlaceholderAppIds() {
        SocialConnectUrlResponse instagram = new InstagramOAuthService(
                "your_instagram_app_id", "test-secret", "http://localhost:8080/api/social/instagram/callback",
                null, null, null, null).createConnectUrl();
        SocialConnectUrlResponse threads = new ThreadsOAuthService(
                "your_threads_app_id", "test-secret", "http://localhost:8080/api/social/threads/callback",
                null, null, null, null).createConnectUrl();

        assertEquals(null, instagram.url());
        assertTrue(instagram.note().contains("INSTAGRAM_APP_ID"));
        assertEquals(null, threads.url());
        assertTrue(threads.note().contains("THREADS_APP_ID"));
    }

    @Test
    void connectUrlsUseConfiguredLocalIdsAndRedirectsWithoutSecrets() {
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        when(currentUserService.requireCurrentUserId()).thenReturn(UUID.randomUUID());
        MetaOAuthStateService stateService = new MetaOAuthStateService();
        String instagram = new InstagramOAuthService(
                "123456789", "test-secret", "http://localhost:8080/api/social/instagram/callback",
                currentUserService, stateService, null, null).createConnectUrl().url();
        String threads = new ThreadsOAuthService(
                "987654321", "test-secret", "http://localhost:8080/api/social/threads/callback",
                currentUserService, stateService, null, null).createConnectUrl().url();

        assertTrue(instagram.contains("client_id=123456789"));
        assertTrue(instagram.contains("redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fapi%2Fsocial%2Finstagram%2Fcallback"));
        assertTrue(threads.contains("client_id=987654321"));
        assertTrue(threads.contains("redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fapi%2Fsocial%2Fthreads%2Fcallback"));
        assertFalse(instagram.contains("test-secret"));
        assertFalse(threads.contains("test-secret"));
    }

    @Test
    void instagramCallbackExchangesLongLivedTokenAndSavesBoundUserAccount() {
        UUID userId = UUID.randomUUID();
        MetaOAuthStateService stateService = new MetaOAuthStateService();
        String state = stateService.issue(userId);
        StubInstagramGraphClient graphClient = new StubInstagramGraphClient(false);
        CapturingAccountService accountService = new CapturingAccountService();
        InstagramOAuthService service = new InstagramOAuthService(
                "123456789", "instagram-secret", "https://example.test/api/social/instagram/callback",
                null, stateService, graphClient, accountService);

        service.complete("authorization-code", state);

        assertEquals("short-instagram-token", graphClient.longLivedInput);
        assertEquals(userId, accountService.userId);
        assertEquals("instagram-user-1", accountService.accountId);
        assertEquals("postiva", accountService.username);
        assertEquals("long-instagram-token", accountService.accessToken);
        assertTrue(accountService.expiresAt.isAfter(LocalDateTime.now().plusDays(50)));
    }

    @Test
    void threadsCallbackExchangesLongLivedTokenAndSavesBoundUserAccount() {
        UUID userId = UUID.randomUUID();
        MetaOAuthStateService stateService = new MetaOAuthStateService();
        String state = stateService.issue(userId);
        StubThreadsGraphClient graphClient = new StubThreadsGraphClient();
        CapturingAccountService accountService = new CapturingAccountService();
        ThreadsOAuthService service = new ThreadsOAuthService(
                "987654321", "threads-secret", "https://example.test/api/social/threads/callback",
                null, stateService, graphClient, accountService);

        service.complete("authorization-code", state);

        assertEquals("short-threads-token", graphClient.longLivedInput);
        assertEquals(userId, accountService.userId);
        assertEquals("threads-user-1", accountService.accountId);
        assertEquals("postiva", accountService.username);
        assertEquals("long-threads-token", accountService.accessToken);
    }

    @Test
    void invalidStateStopsInstagramBeforeTokenExchange() {
        StubInstagramGraphClient graphClient = new StubInstagramGraphClient(false);
        InstagramOAuthService service = new InstagramOAuthService(
                "123456789", "instagram-secret", "https://example.test/api/social/instagram/callback",
                null, new MetaOAuthStateService(), graphClient, new CapturingAccountService());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.complete("authorization-code", "invalid-state"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals(0, graphClient.exchangeCalls);
    }

    @Test
    void tokenExchangeFailureDoesNotLeaveReusableState() {
        MetaOAuthStateService stateService = new MetaOAuthStateService();
        String state = stateService.issue(UUID.randomUUID());
        InstagramOAuthService service = new InstagramOAuthService(
                "123456789", "instagram-secret", "https://example.test/api/social/instagram/callback",
                null, stateService, new StubInstagramGraphClient(true), new CapturingAccountService());

        ResponseStatusException exchangeFailure = assertThrows(
                ResponseStatusException.class,
                () -> service.complete("authorization-code", state));
        ResponseStatusException reusedState = assertThrows(
                ResponseStatusException.class,
                () -> service.complete("authorization-code", state));

        assertEquals(HttpStatus.BAD_GATEWAY, exchangeFailure.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, reusedState.getStatusCode());
    }

    private static final class StubInstagramGraphClient extends InstagramGraphClient {
        private final boolean failExchange;
        private int exchangeCalls;
        private String longLivedInput;

        private StubInstagramGraphClient(boolean failExchange) {
            super(WebClient.builder(), new ObjectMapper(), "v26.0");
            this.failExchange = failExchange;
        }

        @Override
        public OAuthToken exchangeCode(String appId, String appSecret, String redirectUri, String code) {
            exchangeCalls++;
            if (failExchange) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Provider token exchange failed");
            }
            return new OAuthToken("short-instagram-token", "instagram-user-1", 3_600);
        }

        @Override
        public OAuthToken exchangeLongLived(String appSecret, String accessToken) {
            longLivedInput = accessToken;
            return new OAuthToken("long-instagram-token", "instagram-user-1", 5_184_000);
        }

        @Override
        public InstagramProfile getMe(String accessToken) {
            assertEquals("long-instagram-token", accessToken);
            return new InstagramProfile("instagram-user-1", "postiva", "BUSINESS", ApiMode.INSTAGRAM_LOGIN);
        }
    }

    private static final class StubThreadsGraphClient extends ThreadsGraphClient {
        private String longLivedInput;

        private StubThreadsGraphClient() {
            super(WebClient.builder(), new ObjectMapper());
        }

        @Override
        public OAuthToken exchangeCode(String appId, String appSecret, String redirectUri, String code) {
            return new OAuthToken("short-threads-token", "threads-user-1", 3_600);
        }

        @Override
        public OAuthToken exchangeLongLived(String appSecret, String accessToken) {
            longLivedInput = accessToken;
            return new OAuthToken("long-threads-token", "threads-user-1", 5_184_000);
        }

        @Override
        public ThreadsProfile getMe(String accessToken) {
            assertEquals("long-threads-token", accessToken);
            return new ThreadsProfile("threads-user-1", "postiva");
        }
    }

    private static final class CapturingAccountService extends SocialAccountService {
        private UUID userId;
        private String accountId;
        private String username;
        private String accessToken;
        private LocalDateTime expiresAt;

        private CapturingAccountService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public SocialAccount saveInstagramAccount(UUID userId,
                                                  String accountId,
                                                  String username,
                                                  String accountType,
                                                  InstagramGraphClient.ApiMode apiMode,
                                                  String accessToken,
                                                  LocalDateTime expiresAt) {
            capture(userId, accountId, username, accessToken, expiresAt);
            return new SocialAccount();
        }

        @Override
        public SocialAccount saveThreadsAccount(UUID userId,
                                                String accountId,
                                                String username,
                                                String accessToken,
                                                LocalDateTime expiresAt) {
            capture(userId, accountId, username, accessToken, expiresAt);
            return new SocialAccount();
        }

        private void capture(UUID userId,
                             String accountId,
                             String username,
                             String accessToken,
                             LocalDateTime expiresAt) {
            this.userId = userId;
            this.accountId = accountId;
            this.username = username;
            this.accessToken = accessToken;
            this.expiresAt = expiresAt;
        }
    }
}
