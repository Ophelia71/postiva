package com.postiva.admin.security;

import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;

/** RFC 6238: HMAC-SHA1, six digits, 30 seconds. Crypto provided by the JDK. */
@Component
public class Totp {
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private final SecureRandom random = new SecureRandom();

    public String newSecret() {
        byte[] bytes = new byte[20];
        random.nextBytes(bytes);
        return encode(bytes);
    }

    public long match(String secret, String candidate, long lastUsedStep, Instant now) {
        if (candidate == null || !candidate.matches("[0-9]{6}")) return -1;
        long step = now.getEpochSecond() / 30;
        for (long value = step - 1; value <= step + 1; value++) {
            if (value > lastUsedStep && MessageDigest.isEqual(
                    code(secret, value).getBytes(StandardCharsets.US_ASCII),
                    candidate.getBytes(StandardCharsets.US_ASCII))) return value;
        }
        return -1;
    }

    public String code(String secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(decode(secret), "HmacSHA1"));
            byte[] digest = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = digest[digest.length - 1] & 15;
            int binary = ((digest[offset] & 127) << 24) | ((digest[offset + 1] & 255) << 16)
                    | ((digest[offset + 2] & 255) << 8) | (digest[offset + 3] & 255);
            return String.format(Locale.ROOT, "%06d", binary % 1_000_000);
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể kiểm tra mã xác thực");
        }
    }

    public static String encode(byte[] bytes) {
        StringBuilder output = new StringBuilder();
        int buffer = 0, bits = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 255);
            bits += 8;
            while (bits >= 5) { bits -= 5; output.append(ALPHABET.charAt((buffer >>> bits) & 31)); }
        }
        if (bits > 0) output.append(ALPHABET.charAt((buffer << (5 - bits)) & 31));
        return output.toString();
    }

    private static byte[] decode(String value) {
        var output = new java.io.ByteArrayOutputStream();
        int buffer = 0, bits = 0;
        for (char character : value.toCharArray()) {
            int digit = ALPHABET.indexOf(character);
            if (digit < 0) throw new IllegalArgumentException("Invalid Base32");
            buffer = (buffer << 5) | digit;
            bits += 5;
            if (bits >= 8) { bits -= 8; output.write((buffer >>> bits) & 255); }
        }
        return output.toByteArray();
    }
}
