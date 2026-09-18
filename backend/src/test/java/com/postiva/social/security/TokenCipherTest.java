package com.postiva.social.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TokenCipherTest {
    @Test
    void encryptsWithRandomIvAndDecryptsToken() {
        TokenCipher cipher = new TokenCipher("a-development-encryption-key");

        String first = cipher.encrypt("page-access-token");
        String second = cipher.encrypt("page-access-token");

        assertNotEquals("page-access-token", first);
        assertNotEquals(first, second);
        assertEquals("page-access-token", cipher.decrypt(first));
        assertEquals("page-access-token", cipher.decrypt(second));
    }
}
