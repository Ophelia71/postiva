package com.postiva.file.controller;

import com.postiva.common.ApiResponse;
import com.postiva.file.dto.UploadedFileResponse;
import com.postiva.file.entity.UploadedFile;
import com.postiva.file.service.FileService;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UploadedFileResponse> upload(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(fileService.upload(file));
    }

    @GetMapping("/{fileId}")
    public ResponseEntity<Resource> get(@PathVariable UUID fileId) {
        UploadedFile file = fileService.requireFile(fileId);
        return response(file, false);
    }

    @GetMapping("/public/{fileId}")
    public ResponseEntity<Resource> getForPlatform(
            @PathVariable UUID fileId,
            @RequestParam("expires") long expiresAt,
            @RequestParam("signature") String signature
    ) {
        UploadedFile file = fileService.requirePublicFile(fileId, expiresAt, signature);
        return response(file, true);
    }

    private ResponseEntity<Resource> response(UploadedFile file, boolean publicLink) {
        String fileName = file.getFileName() == null || file.getFileName().isBlank() ? "media" : file.getFileName();
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(safeFileName(fileName), StandardCharsets.UTF_8)
                .build();
        MediaType contentType = safeMediaType(file.getMimeType());
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff");
        if (publicLink) {
            response.cacheControl(CacheControl.maxAge(java.time.Duration.ofMinutes(5)).cachePublic());
        } else {
            response.cacheControl(CacheControl.noStore());
        }
        return response.body(fileService.resource(file));
    }

    private MediaType safeMediaType(String value) {
        try {
            MediaType mediaType = MediaType.parseMediaType(value);
            boolean allowed = mediaType.equals(MediaType.IMAGE_JPEG)
                    || mediaType.equals(MediaType.IMAGE_PNG)
                    || mediaType.toString().equals("image/webp")
                    || mediaType.toString().equals("video/mp4")
                    || mediaType.toString().equals("video/quicktime")
                    || mediaType.toString().equals("video/webm");
            return allowed ? mediaType : MediaType.APPLICATION_OCTET_STREAM;
        } catch (RuntimeException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private String safeFileName(String value) {
        String name = value.replace('\\', '/');
        int separator = name.lastIndexOf('/');
        if (separator >= 0) name = name.substring(separator + 1);
        name = name.replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isBlank() || name.equals(".") || name.equals("..")) return "media";
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }
}
