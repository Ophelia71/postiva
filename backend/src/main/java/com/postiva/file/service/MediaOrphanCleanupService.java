package com.postiva.file.service;

import com.postiva.file.entity.UploadedFile;
import com.postiva.file.repository.UploadedFileRepository;
import com.postiva.file.storage.LocalFileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MediaOrphanCleanupService {
    private static final Logger log = LoggerFactory.getLogger(MediaOrphanCleanupService.class);
    private final UploadedFileRepository repository;
    private final LocalFileStorage storage;
    private final TransactionTemplate transactionTemplate;

    public MediaOrphanCleanupService(
            UploadedFileRepository repository,
            LocalFileStorage storage,
            PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.storage = storage;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelay = 21_600_000, initialDelay = 600_000)
    public void cleanupOrphans() {
        try {
            List<String> staleUnattachedIds = deleteExpiredUnattachedMetadata();
            deleteIfUnreferenced(staleUnattachedIds);
            deleteUntrackedFiles();
        } catch (RuntimeException exception) {
            log.warn("Media orphan cleanup did not finish; it will retry on the next run.", exception);
        }
    }

    private List<String> deleteExpiredUnattachedMetadata() {
        List<String> deletedPublicIds = transactionTemplate.execute(status -> {
            List<UploadedFile> staleFiles = repository
                    .findTop500ByStorageProviderAndBriefIdIsNullAndPostIdIsNullAndCreatedAtBeforeOrderByCreatedAtAsc(
                            "LOCAL", LocalDateTime.now().minusDays(7));
            List<String> publicIds = staleFiles.stream()
                    .map(UploadedFile::getPublicId)
                    .filter(value -> value != null && !value.isBlank())
                    .distinct()
                    .toList();
            if (!staleFiles.isEmpty()) {
                repository.deleteAll(staleFiles);
                repository.flush();
            }
            return publicIds;
        });
        return deletedPublicIds == null ? List.of() : deletedPublicIds;
    }

    private void deleteIfUnreferenced(Collection<String> candidateIds) {
        for (String publicId : candidateIds) {
            if (!repository.existsByPublicId(publicId)) {
                deleteQuietly(publicId);
            }
        }
    }

    private void deleteUntrackedFiles() {
        List<String> oldGeneratedFiles = storage.findGeneratedFilesOlderThan(Instant.now().minusSeconds(86_400));
        if (oldGeneratedFiles.isEmpty()) return;
        for (int start = 0; start < oldGeneratedFiles.size(); start += 500) {
            List<String> batch = oldGeneratedFiles.subList(start, Math.min(start + 500, oldGeneratedFiles.size()));
            Set<String> referencedIds = repository.findAllByPublicIdIn(batch).stream()
                    .map(UploadedFile::getPublicId)
                    .collect(Collectors.toSet());
            batch.stream()
                    .filter(fileName -> !referencedIds.contains(fileName))
                    .forEach(this::deleteQuietly);
        }
    }

    private void deleteQuietly(String publicId) {
        try {
            storage.delete(publicId);
        } catch (RuntimeException exception) {
            log.warn("Could not remove an orphaned media file; it will be retried.");
        }
    }
}
