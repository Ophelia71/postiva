package com.postiva.file.repository;

import com.postiva.file.entity.UploadedFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface UploadedFileRepository extends JpaRepository<UploadedFile, UUID> {
    List<UploadedFile> findAllByIdInAndUserId(Collection<UUID> ids, UUID userId);
    Optional<UploadedFile> findByIdAndUserId(UUID id, UUID userId);
    List<UploadedFile> findAllByPostIdOrderByCreatedAtAsc(UUID postId);
    Optional<UploadedFile> findFirstByPostIdOrderByCreatedAtAsc(UUID postId);
    List<UploadedFile> findTop500ByStorageProviderAndBriefIdIsNullAndPostIdIsNullAndCreatedAtBeforeOrderByCreatedAtAsc(
            String storageProvider, LocalDateTime cutoff);
    boolean existsByPublicId(String publicId);
    List<UploadedFile> findAllByPublicIdIn(Collection<String> publicIds);
}
