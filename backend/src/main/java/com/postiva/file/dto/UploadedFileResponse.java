package com.postiva.file.dto;

import java.util.UUID;

public record UploadedFileResponse(
        UUID id,
        String fileUrl,
        String fileName,
        String mimeType,
        long fileSize
) {
}
