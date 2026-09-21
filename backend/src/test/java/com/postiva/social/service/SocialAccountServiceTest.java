package com.postiva.social.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.postiva.common.Platform;
import com.postiva.social.client.ThreadsGraphClient;
import com.postiva.social.entity.SocialAccount;
import com.postiva.social.repository.SocialAccountRepository;
import com.postiva.social.security.TokenCipher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialAccountServiceTest {
    private final TokenCipher tokenCipher = new TokenCipher("a-development-encryption-key");
    private RepositoryFake repositoryFake;
    private StubThreadsGraphClient threadsGraphClient;
    private SocialAccountService service;

    @BeforeEach
    void setUp() {
        repositoryFake = new RepositoryFake();
        threadsGraphClient = new StubThreadsGraphClient();
        service = new SocialAccountService(
                repositoryFake.repository(),
                null,
                tokenCipher,
                null,
                null,
                threadsGraphClient
        );
    }

    @Test
    void refreshesExpiringThreadsTokenBeforePublishing() {
        UUID accountId = UUID.randomUUID();
        SocialAccount account = threadsAccount(
                LocalDateTime.now().plusDays(2),
                LocalDateTime.now().minusDays(2)
        );
        repositoryFake.returnAccount(accountId, account);

        SocialAccount result = service.prepareForPublishing(accountId);

        assertEquals("new-token", tokenCipher.decrypt(result.getAccessTokenEncrypted()));
        assertTrue(result.getTokenExpiresAt().isAfter(LocalDateTime.now().plusDays(50)));
        assertTrue(result.getAccountMetadata().containsKey("tokenRefreshedAt"));
        assertEquals(1, threadsGraphClient.refreshCalls);
        assertEquals(1, repositoryFake.saveCalls);
    }

    @Test
    void keepsHealthyThreadsTokenWithoutCallingRefreshEndpoint() {
        UUID accountId = UUID.randomUUID();
        SocialAccount account = threadsAccount(
                LocalDateTime.now().plusDays(40),
                LocalDateTime.now().minusDays(2)
        );
        repositoryFake.returnAccount(accountId, account);

        SocialAccount result = service.prepareForPublishing(accountId);

        assertEquals("old-token", tokenCipher.decrypt(result.getAccessTokenEncrypted()));
        assertEquals(0, threadsGraphClient.refreshCalls);
        assertEquals(0, repositoryFake.saveCalls);
    }

    @Test
    void removesDisconnectedProviderAccountDuringDataDeletion() {
        SocialAccount account = threadsAccount(
                LocalDateTime.now().plusDays(40),
                LocalDateTime.now().minusDays(2)
        );
        account.setProviderAccountId("threads-user");
        account.setDisconnectedAt(LocalDateTime.now().minusDays(1));
        repositoryFake.providerAccounts.add(account);

        int deleted = service.removeProviderUserData(Platform.THREADS, "threads-user", true);

        assertEquals(1, deleted);
        assertEquals(List.of(account), repositoryFake.deletedAccounts);
    }

    @Test
    void deauthorizationClearsStoredToken() {
        SocialAccount account = threadsAccount(
                LocalDateTime.now().plusDays(40),
                LocalDateTime.now().minusDays(2)
        );
        account.setAccountMetadata(Map.of("oauthUserId", "threads-user"));
        repositoryFake.providerAccounts.add(account);

        int affected = service.removeProviderUserData(Platform.THREADS, "threads-user", false);

        assertEquals(1, affected);
        assertEquals(null, account.getAccessTokenEncrypted());
        assertEquals(null, account.getTokenExpiresAt());
        assertTrue(account.getDisconnectedAt() != null);
        assertEquals(1, repositoryFake.saveAllCalls);
    }

    private SocialAccount threadsAccount(LocalDateTime expiresAt, LocalDateTime connectedAt) {
        SocialAccount account = new SocialAccount();
        account.setProvider(Platform.THREADS);
        account.setAccessTokenEncrypted(tokenCipher.encrypt("old-token"));
        account.setTokenExpiresAt(expiresAt);
        account.setConnectedAt(connectedAt);
        return account;
    }

    private static final class StubThreadsGraphClient extends ThreadsGraphClient {
        private int refreshCalls;

        private StubThreadsGraphClient() {
            super(WebClient.builder(), new ObjectMapper());
        }

        @Override
        public OAuthToken refreshLongLived(String accessToken) {
            refreshCalls++;
            assertEquals("old-token", accessToken);
            return new OAuthToken("new-token", "threads-user", 5_184_000);
        }
    }

    private static final class RepositoryFake {
        private UUID accountId;
        private SocialAccount account;
        private int saveCalls;
        private int saveAllCalls;
        private final List<SocialAccount> providerAccounts = new ArrayList<>();
        private List<SocialAccount> deletedAccounts = List.of();

        private void returnAccount(UUID accountId, SocialAccount account) {
            this.accountId = accountId;
            this.account = account;
        }

        private SocialAccountRepository repository() {
            return (SocialAccountRepository) Proxy.newProxyInstance(
                    SocialAccountRepository.class.getClassLoader(),
                    new Class<?>[]{SocialAccountRepository.class},
                    (proxy, method, arguments) -> switch (method.getName()) {
                        case "findByIdAndDisconnectedAtIsNull" -> {
                            UUID requestedId = (UUID) arguments[0];
                            yield accountId != null && accountId.equals(requestedId)
                                    ? Optional.of(account)
                                    : Optional.empty();
                        }
                        case "save" -> {
                            saveCalls++;
                            yield arguments[0];
                        }
                        case "findByProvider" -> List.copyOf(providerAccounts);
                        case "saveAll" -> {
                            saveAllCalls++;
                            yield arguments[0];
                        }
                        case "deleteAll" -> {
                            @SuppressWarnings("unchecked")
                            Iterable<SocialAccount> accounts = (Iterable<SocialAccount>) arguments[0];
                            List<SocialAccount> captured = new ArrayList<>();
                            accounts.forEach(captured::add);
                            deletedAccounts = List.copyOf(captured);
                            yield null;
                        }
                        case "toString" -> "SocialAccountRepositoryFake";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == arguments[0];
                        default -> throw new UnsupportedOperationException(method.getName());
                    }
            );
        }
    }
}
