package com.postiva.file.service;

import com.postiva.auth.service.CurrentUserService;
import com.postiva.file.entity.UploadedFile;
import com.postiva.file.repository.UploadedFileRepository;
import com.postiva.file.security.MediaAccessSigner;
import com.postiva.file.storage.LocalFileStorage;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FileServiceAccessTest {
    private final UploadedFileRepository repository = mock(UploadedFileRepository.class);
    private final CurrentUserService currentUserService = mock(CurrentUserService.class);
    private final FileService service = new FileService(
            repository,
            new LocalFileStorage("target/test-media", 1024),
            currentUserService,
            new MediaAccessSigner("media-secret-0123456789-abcdefghijkl", 3600),
            "https://api.example.test"
    );

    @Test
    void downloadLooksUpFileByIdAndAuthenticatedOwner() {
        UUID fileId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UploadedFile file = new UploadedFile();
        file.setStorageProvider("LOCAL");
        when(currentUserService.requireCurrentUserId()).thenReturn(ownerId);
        when(repository.findByIdAndUserId(fileId, ownerId)).thenReturn(Optional.of(file));

        assertSame(file, service.requireFile(fileId));

        verify(repository).findByIdAndUserId(fileId, ownerId);
        verify(repository, never()).findById(fileId);
    }

    @Test
    void hidesFileExistenceWhenItBelongsToAnotherUser() {
        UUID fileId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        when(currentUserService.requireCurrentUserId()).thenReturn(currentUserId);
        when(repository.findByIdAndUserId(fileId, currentUserId)).thenReturn(Optional.empty());

        var error = assertThrows(ResponseStatusException.class, () -> service.requireFile(fileId));

        assertEquals(404, error.getStatusCode().value());
        verify(repository, never()).findById(fileId);
    }

    @Test
    void givesMetaATemporarySignedHttpsUrlForLocalMedia() {
        UUID fileId = UUID.randomUUID();
        UploadedFile file = new UploadedFile();
        ReflectionTestUtils.setField(file, "id", fileId);
        file.setFileUrl("/api/files/" + fileId);
        file.setStorageProvider("LOCAL");

        String publicUrl = service.publicUrl(file);

        assertTrue(publicUrl.startsWith("https://api.example.test/api/files/public/" + fileId + "?"));
        String[] parameters = java.net.URI.create(publicUrl).getRawQuery().split("&");
        long expiresAt = Long.parseLong(parameters[0].substring("expires=".length()));
        String signature = parameters[1].substring("signature=".length());
        assertTrue(new MediaAccessSigner("media-secret-0123456789-abcdefghijkl", 3600)
                .isValid(fileId, expiresAt, signature));
    }
}
