package com.postiva.social.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TokenCipherTest {
    @Test
    void encryptsWithRandomIvAndDecryptsToken() {
        TokenCipher cipher = new TokenCipher("a-development-encryption-key-that-is-32-bytes");

        String first = cipher.encrypt("page-access-token");
        String second = cipher.encrypt("page-access-token");

        assertNotEquals("page-access-token", first);
        assertNotEquals(first, second);
        assertEquals("page-access-token", cipher.decrypt(first));
        assertEquals("page-access-token", cipher.decrypt(second));
    }

    @Test
    void rejectsShortEncryptionKeys() {
        TokenCipher cipher = new TokenCipher("too-short");

        assertThrows(IllegalStateException.class, () -> cipher.encrypt("page-access-token"));
    }
}
