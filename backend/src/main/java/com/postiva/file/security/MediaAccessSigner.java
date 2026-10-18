package com.postiva.file.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;

@Component
public class MediaAccessSigner {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final byte[] secret;
    private final long linkTtlSeconds;

    public MediaAccessSigner(
            @Value("${postiva.storage.signing-secret:}") String secret,
            @Value("${postiva.storage.public-link-ttl-seconds:86400}") long linkTtlSeconds
    ) {
        this.secret = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        this.linkTtlSeconds = Math.max(60, Math.min(linkTtlSeconds, 604800));
    }

    public long expiresAtEpochSeconds() {
        return Math.addExact(System.currentTimeMillis() / 1000, linkTtlSeconds);
    }

    public String sign(UUID fileId, long expiresAtEpochSeconds) {
        requireConfiguredSecret();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                hmac(payload(fileId, expiresAtEpochSeconds))
        );
    }

    public boolean isValid(UUID fileId, long expiresAtEpochSeconds, String signature) {
        if (fileId == null || signature == null || signature.isBlank() || secret.length < 32) return false;
        long now = System.currentTimeMillis() / 1000;
        if (expiresAtEpochSeconds <= now || expiresAtEpochSeconds > now + linkTtlSeconds) return false;
        try {
            byte[] provided = Base64.getUrlDecoder().decode(signature);
            byte[] expected = hmac(payload(fileId, expiresAtEpochSeconds));
            return MessageDigest.isEqual(expected, provided);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private byte[] hmac(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể tạo chữ ký URL media", exception);
        }
    }

    private String payload(UUID fileId, long expiresAtEpochSeconds) {
        return fileId + ":" + expiresAtEpochSeconds;
    }

    private void requireConfiguredSecret() {
        if (secret.length < 32) {
            throw new IllegalStateException(
                    "Cần cấu hình POSTIVA_MEDIA_SIGNING_SECRET có ít nhất 32 byte để Meta tải media an toàn."
            );
        }
    }
}
