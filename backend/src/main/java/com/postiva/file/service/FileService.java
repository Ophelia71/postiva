package com.postiva.file.service;

import com.postiva.auth.service.CurrentUserService;
import com.postiva.file.dto.UploadedFileResponse;
import com.postiva.file.entity.UploadedFile;
import com.postiva.file.repository.UploadedFileRepository;
import com.postiva.file.storage.LocalFileStorage;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FileService {
    private final UploadedFileRepository repository;
    private final LocalFileStorage storage;
    private final CurrentUserService currentUserService;

    public FileService(UploadedFileRepository repository,
                       LocalFileStorage storage,
                       CurrentUserService currentUserService) {
        this.repository = repository;
        this.storage = storage;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public UploadedFileResponse upload(MultipartFile file) {
        UUID userId = currentUserService.requireCurrentUserId();
        LocalFileStorage.StoredImage stored = storage.store(file);
        UploadedFile entity = new UploadedFile();
        entity.setUserId(userId);
        entity.setFileUrl("pending");
        entity.setFileName(stored.originalName());
        entity.setMimeType(stored.mimeType());
        entity.setFileSize(stored.size());
        entity.setPublicId(stored.storedName());
        entity.setStorageProvider("LOCAL");
        entity = repository.save(entity);
        entity.setFileUrl("/api/files/" + entity.getId());
        return toResponse(repository.save(entity));
    }

    @Transactional
    public String attachToPost(List<UUID> imageIds, UUID userId, UUID briefId, UUID postId) {
        if (imageIds == null || imageIds.isEmpty()) {
            return null;
        }
        if (imageIds.size() > 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mỗi bài tối đa 10 ảnh");
        }
        List<UploadedFile> files = repository.findAllByIdInAndUserId(imageIds, userId);
        if (files.size() != imageIds.stream().distinct().count()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Có ảnh không tồn tại hoặc không thuộc tài khoản này");
        }
        Map<UUID, UploadedFile> byId = files.stream()
                .collect(Collectors.toMap(UploadedFile::getId, Function.identity()));
        for (UUID imageId : imageIds) {
            UploadedFile file = byId.get(imageId);
            file.setBriefId(briefId);
            file.setPostId(postId);
        }
        repository.saveAll(files);
        return byId.get(imageIds.get(0)).getFileUrl();
    }

    @Transactional(readOnly = true)
    public UploadedFile requireFile(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy ảnh"));
    }

    @Transactional(readOnly = true)
    public UploadedFile firstImageForPost(UUID postId) {
        return repository.findFirstByPostIdOrderByCreatedAtAsc(postId).orElse(null);
    }

    public FileSystemResource resource(UploadedFile file) {
        return storage.resource(file.getPublicId());
    }

    private UploadedFileResponse toResponse(UploadedFile file) {
        return new UploadedFileResponse(
                file.getId(), file.getFileUrl(), file.getFileName(), file.getMimeType(), file.getFileSize()
        );
    }
}
