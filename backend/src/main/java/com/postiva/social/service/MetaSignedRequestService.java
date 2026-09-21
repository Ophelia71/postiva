package com.postiva.social.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.postiva.common.Platform;
import com.postiva.social.entity.SocialDataDeletionRequest;
import com.postiva.social.repository.SocialDataDeletionRequestRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Service
public class MetaSignedRequestService {
    private static final String HMAC_SHA_256 = "HmacSHA256";
    private final ObjectMapper objectMapper;
    private final SocialAccountService accountService;
    private final SocialDataDeletionRequestRepository deletionRequestRepository;
    private final Map<Platform, String> appSecrets;

    public MetaSignedRequestService(
            ObjectMapper objectMapper,
            SocialAccountService accountService,
            SocialDataDeletionRequestRepository deletionRequestRepository,
            @Value("${postiva.meta.app-secret:}") String facebookAppSecret,
            @Value("${postiva.instagram.app-secret:}") String instagramAppSecret,
            @Value("${postiva.threads.app-secret:}") String threadsAppSecret
    ) {
        this.objectMapper = objectMapper;
        this.accountService = accountService;
        this.deletionRequestRepository = deletionRequestRepository;
        this.appSecrets = Map.of(
                Platform.FACEBOOK, normalizeSecret(facebookAppSecret),
                Platform.INSTAGRAM, normalizeSecret(instagramAppSecret),
                Platform.THREADS, normalizeSecret(threadsAppSecret)
        );
    }

    public DeauthorizationResponse deauthorize(Platform provider, String signedRequest) {
        SignedRequestPayload payload = verify(provider, signedRequest);
        int affectedAccounts = accountService.removeProviderUserData(provider, payload.userId(), false);
        return new DeauthorizationResponse(true, affectedAccounts);
    }

    @Transactional
    public DeletionReceipt deleteData(Platform provider, String signedRequest) {
        SignedRequestPayload payload = verify(provider, signedRequest);
        int deletedAccounts = accountService.removeProviderUserData(provider, payload.userId(), true);

        String confirmationCode = UUID.randomUUID().toString();
        SocialDataDeletionRequest request = new SocialDataDeletionRequest();
        request.setConfirmationCode(confirmationCode);
        request.setProvider(provider);
        request.setStatus("completed");
        request.setDeletedAccounts(deletedAccounts);
        request.setCompletedAt(LocalDateTime.now());
        deletionRequestRepository.save(request);
        return new DeletionReceipt(confirmationCode, deletedAccounts);
    }

    @Transactional(readOnly = true)
    public DeletionStatus getDeletionStatus(Platform provider, String confirmationCode) {
        SocialDataDeletionRequest request = deletionRequestRepository
                .findByConfirmationCodeAndProvider(confirmationCode, provider)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu xóa dữ liệu"));
        return new DeletionStatus(
                request.getConfirmationCode(),
                request.getProvider(),
                request.getStatus(),
                request.getDeletedAccounts(),
                request.getCompletedAt()
        );
    }

    private SignedRequestPayload verify(Platform provider, String signedRequest) {
        String appSecret = appSecrets.get(provider);
        if (appSecret == null || appSecret.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Chưa cấu hình App Secret cho " + provider.name()
            );
        }
        if (signedRequest == null || signedRequest.isBlank()) {
            throw invalidSignedRequest();
        }

        String[] parts = signedRequest.trim().split("\\.", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw invalidSignedRequest();
        }

        try {
            byte[] actualSignature = Base64.getUrlDecoder().decode(parts[0]);
            Mac mac = Mac.getInstance(HMAC_SHA_256);
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA_256));
            byte[] expectedSignature = mac.doFinal(parts[1].getBytes(StandardCharsets.US_ASCII));
            if (!java.security.MessageDigest.isEqual(actualSignature, expectedSignature)) {
                throw invalidSignedRequest();
            }

            JsonNode payload = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
            if (!"HMAC-SHA256".equalsIgnoreCase(payload.path("algorithm").asText())) {
                throw invalidSignedRequest();
            }
            String userId = payload.path("user_id").asText().trim();
            if (userId.isBlank()) {
                throw invalidSignedRequest();
            }
            return new SignedRequestPayload(userId);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidSignedRequest();
        }
    }

    private String normalizeSecret(String value) {
        return value == null ? "" : value.trim();
    }

    private ResponseStatusException invalidSignedRequest() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Meta signed_request không hợp lệ");
    }

    private record SignedRequestPayload(String userId) {
    }

    public record DeauthorizationResponse(boolean received, int affectedAccounts) {
    }

    public record DeletionReceipt(String confirmationCode, int deletedAccounts) {
    }

    public record DataDeletionResponse(
            String url,
            @JsonProperty("confirmation_code") String confirmationCode
    ) {
    }

    public record DeletionStatus(
            @JsonProperty("confirmation_code") String confirmationCode,
            Platform provider,
            String status,
            int deletedAccounts,
            LocalDateTime completedAt
    ) {
    }
}
