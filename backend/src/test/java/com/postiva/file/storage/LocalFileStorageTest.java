package com.postiva.file.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class LocalFileStorageTest {
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/iwAAAABJRU5ErkJggg=="
    );

    @TempDir
    Path tempDir;

    @Test
    void detectsContentInsteadOfTrustingClientMimeAndSanitizesFileName() {
        var storage = new LocalFileStorage(tempDir.toString(), 1024);
        var upload = new MockMultipartFile("file", "../../image.png", "text/html", PNG);

        var stored = storage.store(upload);

        assertEquals("image/png", stored.mimeType());
        assertEquals("image.png", stored.originalName());
        assertTrue(storage.resource(stored.storedName()).exists());
    }

    @Test
    void rejectsAnHtmlPayloadClaimingToBeAnImage() {
        var storage = new LocalFileStorage(tempDir.toString(), 1024);
        var upload = new MockMultipartFile("file", "fake.png", "image/png", "<html>not an image</html>".getBytes());

        var error = assertThrows(ResponseStatusException.class, () -> storage.store(upload));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void enforcesActualUploadLimit() {
        var storage = new LocalFileStorage(tempDir.toString(), 16);
        var upload = new MockMultipartFile("file", "large.png", "image/png", PNG);

        var error = assertThrows(ResponseStatusException.class, () -> storage.store(upload));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, error.getStatusCode());
    }

    @Test
    void cleanupScanOnlyReturnsOldSystemGeneratedMediaNames() throws Exception {
        var storage = new LocalFileStorage(tempDir.toString(), 1024);
        String generated = "5f765704-4c4e-452e-8a37-85e44c1ba8bf.png";
        java.nio.file.Files.write(tempDir.resolve(generated), PNG);
        java.nio.file.Files.writeString(tempDir.resolve("keep-me.txt"), "unrelated");

        var candidates = storage.findGeneratedFilesOlderThan(java.time.Instant.now().plusSeconds(1));

        assertEquals(java.util.List.of(generated), candidates);
    }
}
