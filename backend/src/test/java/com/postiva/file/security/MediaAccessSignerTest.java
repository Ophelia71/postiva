package com.postiva.file.security;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MediaAccessSignerTest {
    private final MediaAccessSigner signer = new MediaAccessSigner("media-secret-0123456789-abcdefghijkl", 3600);

    @Test
    void signedLinkIsBoundToFileAndExpiry() {
        UUID fileId = UUID.randomUUID();
        long expiresAt = signer.expiresAtEpochSeconds();
        String signature = signer.sign(fileId, expiresAt);

        assertTrue(signer.isValid(fileId, expiresAt, signature));
        assertFalse(signer.isValid(UUID.randomUUID(), expiresAt, signature));
        assertFalse(signer.isValid(fileId, expiresAt - 1, signature));
    }

    @Test
    void rejectsExpiredAndExcessivelyLongLinks() {
        UUID fileId = UUID.randomUUID();
        long expired = System.currentTimeMillis() / 1000 - 10;
        long tooFar = System.currentTimeMillis() / 1000 + 3601;

        assertFalse(signer.isValid(fileId, expired, signer.sign(fileId, expired)));
        assertFalse(signer.isValid(fileId, tooFar, signer.sign(fileId, tooFar)));
    }

    @Test
    void requiresStrongSecretWhenCreatingExternalLinks() {
        var weakSigner = new MediaAccessSigner("weak", 3600);

        assertThrows(IllegalStateException.class,
                () -> weakSigner.sign(UUID.randomUUID(), weakSigner.expiresAtEpochSeconds()));
    }
}
