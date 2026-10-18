package com.postiva.file.service;

import com.postiva.file.entity.UploadedFile;
import com.postiva.file.repository.UploadedFileRepository;
import com.postiva.file.storage.LocalFileStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MediaOrphanCleanupServiceTest {
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/iwAAAABJRU5ErkJggg=="
    );

    @TempDir
    Path tempDir;

    @Test
    void removesExpiredUnattachedRowsAndOldDiskFilesWithoutDatabaseReferences() throws Exception {
        var repository = mock(UploadedFileRepository.class);
        var transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        doNothing().when(transactionManager).commit(any());
        var storage = new LocalFileStorage(tempDir.toString(), 1024);
        var uploaded = storage.store(new MockMultipartFile("file", "abandoned.png", "image/png", PNG));
        UploadedFile staleRow = new UploadedFile();
        staleRow.setPublicId(uploaded.storedName());
        when(repository.findTop500ByStorageProviderAndBriefIdIsNullAndPostIdIsNullAndCreatedAtBeforeOrderByCreatedAtAsc(eq("LOCAL"), any()))
                .thenReturn(List.of(staleRow));
        when(repository.existsByPublicId(uploaded.storedName())).thenReturn(false);

        String diskOnly = UUID.randomUUID() + ".png";
        Path diskOnlyPath = tempDir.resolve(diskOnly);
        Files.write(diskOnlyPath, PNG);
        Files.setLastModifiedTime(diskOnlyPath, FileTime.from(Instant.now().minus(2, ChronoUnit.DAYS)));
        when(repository.findAllByPublicIdIn(any())).thenReturn(List.of());

        new MediaOrphanCleanupService(repository, storage, transactionManager).cleanupOrphans();

        verify(repository).deleteAll(List.of(staleRow));
        verify(repository).flush();
        assertFalse(Files.exists(tempDir.resolve(uploaded.storedName())));
        assertFalse(Files.exists(diskOnlyPath));
    }
}
