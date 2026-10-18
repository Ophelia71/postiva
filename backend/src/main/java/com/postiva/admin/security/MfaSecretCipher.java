package com.postiva.admin.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

@Component
public class MfaSecretCipher {
    private final String key;
    private final SecureRandom random = new SecureRandom();
    public MfaSecretCipher(@Value("${postiva.security.mfa-encryption-key:}") String key) { this.key = key; }
    public boolean isConfigured() { return key != null && key.getBytes(StandardCharsets.UTF_8).length >= 32; }

    public String encrypt(UUID user, String value) { return transform(user, value, true); }
    public String decrypt(UUID user, String value) { return transform(user, value, false); }

    private String transform(UUID user, String value, boolean encrypt) {
        if (!isConfigured()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "MFA chưa được cấu hình khóa mã hóa trên máy chủ");
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            byte[] data = encrypt ? value.getBytes(StandardCharsets.UTF_8) : Base64.getDecoder().decode(value);
            byte[] iv = new byte[12];
            if (encrypt) random.nextBytes(iv);
            else { System.arraycopy(data, 0, iv, 0, iv.length); }
            byte[] keyBytes = MessageDigest.getInstance("SHA-256").digest(("postiva-mfa:" + key).getBytes(StandardCharsets.UTF_8));
            cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE,
                    new SecretKeySpec(keyBytes, "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD(user.toString().getBytes(StandardCharsets.UTF_8));
            if (encrypt) {
                byte[] encrypted = cipher.doFinal(data);
                return Base64.getEncoder().encodeToString(ByteBuffer.allocate(12 + encrypted.length).put(iv).put(encrypted).array());
            }
            return new String(cipher.doFinal(data, 12, data.length - 12), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Không thể xử lý dữ liệu MFA; liên hệ người vận hành");
        }
    }
}
