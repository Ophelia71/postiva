package com.postiva.admin.security;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class TotpTest {
    private final Totp totp = new Totp();

    @Test
    void matchesRfc6238Sha1VectorsForSixDigits() {
        String secret = Totp.encode("12345678901234567890".getBytes(StandardCharsets.US_ASCII));
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", secret);
        long[] seconds = {59, 1111111109L, 1111111111L, 1234567890L, 2000000000L, 20000000000L};
        String[] codes = {"287082", "081804", "050471", "005924", "279037", "353130"};
        for (int i = 0; i < seconds.length; i++) assertEquals(codes[i], totp.code(secret, seconds[i] / 30));
    }

    @Test
    void rejectsReplaysExpiredAndMalformedCodes() {
        String secret = totp.newSecret();
        Instant now = Instant.ofEpochSecond(1234567890);
        long step = now.getEpochSecond() / 30;
        String code = totp.code(secret, step);
        assertEquals(step, totp.match(secret, code, -1, now));
        assertEquals(-1, totp.match(secret, code, step, now));
        assertEquals(-1, totp.match(secret, code, -1, now.plusSeconds(90)));
        assertEquals(-1, totp.match(secret, "bad", -1, now));
    }

    @Test
    void encryptedSecretIsBoundToItsAdminAndUsesRandomNonce() {
        MfaSecretCipher cipher = new MfaSecretCipher("only-a-long-test-key-with-at-least-32-bytes");
        UUID user = UUID.randomUUID();
        String secret = totp.newSecret();
        String encrypted = cipher.encrypt(user, secret);
        assertNotEquals(encrypted, cipher.encrypt(user, secret));
        assertEquals(secret, cipher.decrypt(user, encrypted));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> cipher.decrypt(UUID.randomUUID(), encrypted));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> new MfaSecretCipher("").encrypt(user, secret));
    }
}
