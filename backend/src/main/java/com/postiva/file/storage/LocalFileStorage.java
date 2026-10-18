package com.postiva.file.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Component
public class LocalFileStorage {
    private static final long DEFAULT_MAX_SIZE = 10L * 1024L * 1024L;
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp",
            "video/mp4", "video/quicktime", "video/webm"
    );
    private static final Pattern GENERATED_FILE = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp|mp4|mov|webm)"
    );

    private final Path uploadRoot;
    private final long maxSize;

    public LocalFileStorage(
            @Value("${postiva.storage.upload-dir}") String uploadDir,
            @Value("${postiva.storage.max-file-size-bytes:10485760}") long maxSize
    ) {
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
        this.maxSize = maxSize > 0 ? maxSize : DEFAULT_MAX_SIZE;
    }

    public StoredImage store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bạn chưa chọn file media");
        }
        if (file.getSize() > maxSize) {
            throw tooLarge();
        }

        String mimeType = detectMimeType(file);
        if (!ALLOWED_TYPES.contains(mimeType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Nội dung file không hợp lệ. Chỉ hỗ trợ ảnh JPG/PNG/WebP hoặc video MP4/MOV/WebM");
        }

        String storedName = UUID.randomUUID() + extension(mimeType);
        Path target = resolve(storedName);
        try {
            Files.createDirectories(uploadRoot);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target);
            }
            long actualSize = Files.size(target);
            if (actualSize == 0) {
                Files.deleteIfExists(target);
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File media rỗng hoặc không hợp lệ");
            }
            if (actualSize > maxSize) {
                Files.deleteIfExists(target);
                throw tooLarge();
            }
            return new StoredImage(storedName, sanitizeOriginalName(file.getOriginalFilename()), mimeType, actualSize);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (IOException exception) {
            deleteQuietly(target);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể lưu file media", exception);
        }
    }

    public FileSystemResource resource(String storedName) {
        Path path = resolve(storedName);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy ảnh");
        }
        return new FileSystemResource(path);
    }

    public void delete(String storedName) {
        if (storedName == null || !GENERATED_FILE.matcher(storedName).matches()) {
            throw new IllegalArgumentException("Chỉ có thể xóa file media do hệ thống tạo");
        }
        try {
            Files.deleteIfExists(resolve(storedName));
        } catch (IOException exception) {
            throw new IllegalStateException("Không thể dọn file media", exception);
        }
    }

    public List<String> findGeneratedFilesOlderThan(Instant cutoff) {
        if (!Files.isDirectory(uploadRoot, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        try (Stream<Path> paths = Files.list(uploadRoot)) {
            paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> GENERATED_FILE.matcher(path.getFileName().toString()).matches())
                    .filter(path -> {
                        try {
                            return Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(cutoff);
                        } catch (IOException ignored) {
                            return false;
                        }
                    })
                    .map(path -> path.getFileName().toString())
                    .forEach(result::add);
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Không thể quét thư mục media để dọn file mồ côi", exception);
        }
    }

    private String detectMimeType(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(16);
            if (file.getSize() >= 16 && startsWith(header, 0xFF, 0xD8, 0xFF)) {
                return hasValidImageHeader(file) ? "image/jpeg" : "";
            }
            if (file.getSize() >= 57 && startsWith(header, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
                return hasValidImageHeader(file) ? "image/png" : "";
            }
            if (file.getSize() >= 20 && header.length >= 16
                    && ascii(header, 0, 4).equals("RIFF") && ascii(header, 8, 4).equals("WEBP")
                    && Set.of("VP8 ", "VP8L", "VP8X").contains(ascii(header, 12, 4))) {
                return "image/webp";
            }
            if (file.getSize() >= 16 && startsWith(header, 0x1A, 0x45, 0xDF, 0xA3)) return "video/webm";
            if (file.getSize() >= 24 && header.length >= 12 && ascii(header, 4, 4).equals("ftyp")) {
                String brand = ascii(header, 8, 4);
                if (brand.equals("qt  ")) return "video/quicktime";
                if (Set.of("isom", "iso2", "iso3", "iso4", "iso5", "iso6", "mp41", "mp42", "mp71", "mp21",
                        "avc1", "M4V ", "M4A ", "M4B ", "M4P ", "M4VH", "M4VP", "MSNV", "F4V ", "dash",
                        "3gp4", "3gp5", "3gp6", "3g2a", "3g2b")
                        .contains(brand)) {
                    return "video/mp4";
                }
            }
            return "";
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể đọc file media", exception);
        }
    }

    private boolean hasValidImageHeader(MultipartFile file) throws IOException {
        try (InputStream input = file.getInputStream(); ImageInputStream image = javax.imageio.ImageIO.createImageInputStream(input)) {
            if (image == null) return false;
            Iterator<ImageReader> readers = javax.imageio.ImageIO.getImageReaders(image);
            if (!readers.hasNext()) return false;
            ImageReader reader = readers.next();
            try {
                reader.setInput(image, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                return width > 0 && height > 0 && (long) width * height <= 40_000_000L;
            } finally {
                reader.dispose();
            }
        }
    }

    private boolean startsWith(byte[] bytes, int... prefix) {
        if (bytes.length < prefix.length) return false;
        for (int index = 0; index < prefix.length; index++) {
            if ((bytes[index] & 0xFF) != prefix[index]) return false;
        }
        return true;
    }

    private String ascii(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length, java.nio.charset.StandardCharsets.US_ASCII);
    }

    private String sanitizeOriginalName(String name) {
        String value = name == null ? "media" : name.replace('\\', '/');
        int slash = value.lastIndexOf('/');
        if (slash >= 0) value = value.substring(slash + 1);
        value = value.replaceAll("[\\p{Cntrl}]", "").trim();
        if (value.isBlank() || value.equals(".") || value.equals("..")) value = "media";
        return value.length() > 255 ? value.substring(value.length() - 255) : value;
    }

    private ResponseStatusException tooLarge() {
        return new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Ảnh/video không được vượt quá 10 MB");
    }

    private Path resolve(String storedName) {
        if (storedName == null || storedName.isBlank() || storedName.contains("..")
                || storedName.contains("/") || storedName.contains("\\")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên file không hợp lệ");
        }
        Path target = uploadRoot.resolve(storedName).normalize();
        if (!target.startsWith(uploadRoot)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đường dẫn file không hợp lệ");
        }
        return target;
    }

    private String extension(String mimeType) {
        return switch (mimeType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "video/mp4" -> ".mp4";
            case "video/quicktime" -> ".mov";
            case "video/webm" -> ".webm";
            default -> "";
        };
    }

    private void deleteQuietly(Path target) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // The scheduled orphan-file sweep will remove a generated file if immediate cleanup fails.
        }
    }

    public record StoredImage(String storedName, String originalName, String mimeType, long size) {
    }
}
