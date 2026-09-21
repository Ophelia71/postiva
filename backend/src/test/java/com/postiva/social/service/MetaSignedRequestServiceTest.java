package com.postiva.social.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.postiva.common.Platform;
import com.postiva.social.entity.SocialDataDeletionRequest;
import com.postiva.social.repository.SocialDataDeletionRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Proxy;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetaSignedRequestServiceTest {
    private static final String FACEBOOK_SECRET = "facebook-secret";
    private static final String INSTAGRAM_SECRET = "instagram-secret";
    private static final String THREADS_SECRET = "threads-secret";

    @Test
    void deauthorizesOnlyAfterValidSignature() throws Exception {
        CapturingAccountService accountService = new CapturingAccountService();
        MetaSignedRequestService service = service(accountService);

        MetaSignedRequestService.DeauthorizationResponse response = service.deauthorize(
                Platform.THREADS,
                signedRequest(THREADS_SECRET, "threads-user-1")
        );

        assertTrue(response.received());
        assertEquals(2, response.affectedAccounts());
        assertEquals(Platform.THREADS, accountService.provider);
        assertEquals("threads-user-1", accountService.providerUserId);
        assertEquals(false, accountService.hardDelete);
    }

    @Test
    void deletesDataAndExposesProviderScopedStatus() throws Exception {
        CapturingAccountService accountService = new CapturingAccountService();
        MetaSignedRequestService service = service(accountService);

        MetaSignedRequestService.DeletionReceipt receipt = service.deleteData(
                Platform.INSTAGRAM,
                signedRequest(INSTAGRAM_SECRET, "instagram-user-1")
        );
        MetaSignedRequestService.DeletionStatus status = service.getDeletionStatus(
                Platform.INSTAGRAM,
                receipt.confirmationCode()
        );

        assertTrue(accountService.hardDelete);
        assertEquals(2, receipt.deletedAccounts());
        assertEquals("completed", status.status());
        assertEquals(Platform.INSTAGRAM, status.provider());
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getDeletionStatus(Platform.FACEBOOK, receipt.confirmationCode())
        );
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void rejectsTamperedSignedRequest() throws Exception {
        MetaSignedRequestService service = service(new CapturingAccountService());
        String request = signedRequest(FACEBOOK_SECRET, "meta-user-1");
        String tampered = request.substring(0, request.length() - 1) + "A";

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.deauthorize(Platform.FACEBOOK, tampered)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    private MetaSignedRequestService service(CapturingAccountService accountService) {
        return new MetaSignedRequestService(
                new ObjectMapper(),
                accountService,
                deletionRepository(),
                FACEBOOK_SECRET,
                INSTAGRAM_SECRET,
                THREADS_SECRET
        );
    }

    private SocialDataDeletionRequestRepository deletionRepository() {
        Map<String, SocialDataDeletionRequest> requests = new HashMap<>();
        return (SocialDataDeletionRequestRepository) Proxy.newProxyInstance(
                SocialDataDeletionRequestRepository.class.getClassLoader(),
                new Class<?>[]{SocialDataDeletionRequestRepository.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "save" -> {
                        SocialDataDeletionRequest request = (SocialDataDeletionRequest) arguments[0];
                        requests.put(request.getConfirmationCode(), request);
                        yield request;
                    }
                    case "findByConfirmationCodeAndProvider" -> {
                        SocialDataDeletionRequest request = requests.get(arguments[0]);
                        yield request != null && request.getProvider() == arguments[1]
                                ? Optional.of(request)
                                : Optional.empty();
                    }
                    case "toString" -> "SocialDataDeletionRequestRepositoryFake";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private String signedRequest(String secret, String userId) throws Exception {
        String json = "{\"algorithm\":\"HMAC-SHA256\",\"user_id\":\"" + userId + "\"}";
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(json.getBytes(StandardCharsets.UTF_8));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII)));
        return signature + "." + payload;
    }

    private static final class CapturingAccountService extends SocialAccountService {
        private Platform provider;
        private String providerUserId;
        private boolean hardDelete;

        private CapturingAccountService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public int removeProviderUserData(Platform provider, String providerUserId, boolean hardDelete) {
            this.provider = provider;
            this.providerUserId = providerUserId;
            this.hardDelete = hardDelete;
            return 2;
        }
    }
}
