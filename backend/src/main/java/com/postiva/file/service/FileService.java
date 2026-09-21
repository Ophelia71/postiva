package com.postiva.file.service;

import com.postiva.auth.service.CurrentUserService;
import com.postiva.file.dto.UploadedFileResponse;
import com.postiva.file.entity.UploadedFile;
import com.postiva.file.repository.UploadedFileRepository;
import com.postiva.file.storage.LocalFileStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FileService {
    private final UploadedFileRepository repository;
    private final LocalFileStorage storage;
    private final CurrentUserService currentUserService;
    private final String publicBaseUrl;

    public FileService(UploadedFileRepository repository,
                       LocalFileStorage storage,
                       CurrentUserService currentUserService,
                       @Value("${postiva.storage.public-base-url:}") String publicBaseUrl) {
        this.repository = repository;
        this.storage = storage;
        this.currentUserService = currentUserService;
        this.publicBaseUrl = normalizeBaseUrl(publicBaseUrl);
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
        List<UUID> distinctImageIds = imageIds.stream().distinct().toList();
        if (distinctImageIds.size() > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mỗi bài tối đa 20 file media");
        }
        List<UploadedFile> files = repository.findAllByIdInAndUserId(distinctImageIds, userId);
        if (files.size() != distinctImageIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Có ảnh không tồn tại hoặc không thuộc tài khoản này");
        }
        Map<UUID, UploadedFile> byId = files.stream()
                .collect(Collectors.toMap(UploadedFile::getId, Function.identity()));
        List<UploadedFile> attachments = new ArrayList<>();
        for (UUID imageId : distinctImageIds) {
            UploadedFile source = byId.get(imageId);
            if (source.getPostId() != null && !source.getPostId().equals(postId)) {
                UploadedFile copy = copyForPost(source, briefId, postId);
                copy = repository.save(copy);
                copy.setFileUrl("/api/files/" + copy.getId());
                attachments.add(repository.save(copy));
            } else {
                source.setBriefId(briefId);
                source.setPostId(postId);
                attachments.add(source);
            }
        }
        repository.saveAll(attachments);
        return attachments.get(0).getFileUrl();
    }

    @Transactional
    public String replacePostMedia(List<UUID> mediaIds, UUID userId, UUID briefId, UUID postId) {
        List<UUID> desiredIds = mediaIds == null ? List.of() : mediaIds.stream().distinct().toList();
        List<UploadedFile> currentAttachments = repository.findAllByPostIdOrderByCreatedAtAsc(postId);
        List<UploadedFile> removedAttachments = currentAttachments.stream()
                .filter(file -> !desiredIds.contains(file.getId()))
                .toList();
        for (UploadedFile file : removedAttachments) {
            file.setPostId(null);
            file.setBriefId(null);
        }
        repository.saveAll(removedAttachments);
        if (desiredIds.isEmpty()) {
            return null;
        }
        return attachToPost(desiredIds, userId, briefId, postId);
    }

    private UploadedFile copyForPost(UploadedFile source, UUID briefId, UUID postId) {
        UploadedFile copy = new UploadedFile();
        copy.setUserId(source.getUserId());
        copy.setBriefId(briefId);
        copy.setPostId(postId);
        copy.setFileUrl(source.getFileUrl());
        copy.setFileName(source.getFileName());
        copy.setMimeType(source.getMimeType());
        copy.setFileSize(source.getFileSize());
        copy.setPublicId(source.getPublicId());
        copy.setStorageProvider(source.getStorageProvider());
        return copy;
    }

    @Transactional(readOnly = true)
    public UploadedFile requireFile(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy ảnh"));
    }

    @Transactional(readOnly = true)
    public UploadedFile firstMediaForPost(UUID postId) {
        return repository.findFirstByPostIdOrderByCreatedAtAsc(postId).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<UploadedFile> allMediaForPost(UUID postId) {
        return repository.findAllByPostIdOrderByCreatedAtAsc(postId);
    }

    public FileSystemResource resource(UploadedFile file) {
        return storage.resource(file.getPublicId());
    }

    public String publicUrl(UploadedFile file) {
        String fileUrl = file == null ? null : file.getFileUrl();
        if (fileUrl != null && (fileUrl.startsWith("https://") || fileUrl.startsWith("http://"))) {
            ensurePublicAddress(fileUrl);
            return fileUrl;
        }
        if (publicBaseUrl.isBlank()) {
            throw new IllegalStateException(
                    "Instagram/Threads cần URL media công khai. Hãy cấu hình POSTIVA_PUBLIC_BASE_URL bằng domain HTTPS của backend."
            );
        }
        String path = fileUrl == null ? "" : fileUrl.trim();
        String result = publicBaseUrl + (path.startsWith("/") ? path : "/" + path);
        ensurePublicAddress(result);
        return result;
    }

    private String normalizeBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim().replaceAll("/+$", "");
    }

    private void ensurePublicAddress(String value) {
        String normalized = value.toLowerCase();
        if (!(normalized.startsWith("https://") || normalized.startsWith("http://"))
                || normalized.contains("://localhost")
                || normalized.contains("://127.0.0.1")
                || normalized.contains("://0.0.0.0")) {
            throw new IllegalStateException(
                    "URL media phải truy cập được công khai từ Meta; localhost không thể dùng cho Instagram/Threads."
            );
        }
    }

    private UploadedFileResponse toResponse(UploadedFile file) {
        return new UploadedFileResponse(
                file.getId(), file.getFileUrl(), file.getFileName(), file.getMimeType(), file.getFileSize()
        );
    }
}
