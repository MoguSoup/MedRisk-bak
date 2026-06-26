package com.hbut.medrisk.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {
    private static final Set<String> BUCKETS = Set.of(
            "knowledge-documents",
            "qa-images",
            "disease-images",
            "medical-case-images",
            "avatars",
            "datasets");

    private final Path uploadRoot;

    public FileStorageService(@Value("${medrisk.upload-dir}") String uploadDir) {
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public StoredFile store(String bucket, MultipartFile file) throws IOException {
        validateBucket(bucket);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        String original = StringUtils.cleanPath(file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename());
        String extension = extension(original);
        String day = LocalDate.now().toString().replace("-", "");
        String objectKey = day + "/" + UUID.randomUUID() + extension;
        Path dir = uploadRoot.resolve(bucket).resolve(day).normalize();
        ensureInside(uploadRoot, dir);
        Files.createDirectories(dir);
        Path target = uploadRoot.resolve(bucket).resolve(objectKey).normalize();
        ensureInside(uploadRoot, target);
        file.transferTo(target);
        return new StoredFile(
                bucket,
                objectKey.replace('\\', '/'),
                original,
                file.getSize(),
                contentType(file.getContentType(), original),
                "/api/files/" + bucket + "/" + objectKey.replace('\\', '/'));
    }

    public StoredFile storeGeneratedText(String bucket, String objectKey, String originalFilename, String content) throws IOException {
        validateBucket(bucket);
        if (objectKey == null || objectKey.isBlank() || objectKey.contains("..")) {
            throw new IllegalArgumentException("文件路径不合法");
        }
        String normalizedKey = objectKey.replace('\\', '/');
        Path target = uploadRoot.resolve(bucket).resolve(normalizedKey).normalize();
        ensureInside(uploadRoot.resolve(bucket).toAbsolutePath().normalize(), target.toAbsolutePath().normalize());
        Files.createDirectories(target.getParent());
        byte[] bytes = (content == null ? "" : content).getBytes(StandardCharsets.UTF_8);
        Files.write(target, bytes);
        String filename = StringUtils.cleanPath(originalFilename == null ? target.getFileName().toString() : originalFilename);
        return new StoredFile(
                bucket,
                normalizedKey,
                filename,
                bytes.length,
                MediaType.TEXT_PLAIN_VALUE,
                "/api/files/" + bucket + "/" + normalizedKey);
    }

    public StoredResource load(String bucket, String objectKey) throws IOException {
        validateBucket(bucket);
        if (objectKey == null || objectKey.isBlank() || objectKey.contains("..")) {
            throw new IllegalArgumentException("文件路径不合法");
        }
        Path target = uploadRoot.resolve(bucket).resolve(objectKey).toAbsolutePath().normalize();
        ensureInside(uploadRoot.resolve(bucket).toAbsolutePath().normalize(), target);
        if (!Files.isRegularFile(target)) {
            throw new IllegalArgumentException("文件不存在");
        }
        String type = Files.probeContentType(target);
        if (type == null || type.isBlank()) {
            type = contentType(null, target.getFileName().toString());
        }
        InputStream input = Files.newInputStream(target);
        return new StoredResource(new InputStreamResource(input), type, target.getFileName().toString(), Files.size(target));
    }

    private void validateBucket(String bucket) {
        if (!BUCKETS.contains(bucket)) {
            throw new IllegalArgumentException("不支持的文件桶");
        }
    }

    private void ensureInside(Path root, Path target) {
        if (!target.normalize().startsWith(root.normalize())) {
            throw new IllegalArgumentException("文件路径越界");
        }
    }

    private String extension(String filename) {
        int index = filename.lastIndexOf('.');
        if (index < 0) {
            return "";
        }
        String ext = filename.substring(index).toLowerCase(Locale.ROOT);
        return ext.length() > 16 ? "" : ext;
    }

    private String contentType(String provided, String filename) {
        if (provided != null && !provided.isBlank()) {
            return provided;
        }
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) return MediaType.IMAGE_PNG_VALUE;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return MediaType.IMAGE_JPEG_VALUE;
        if (lower.endsWith(".pdf")) return MediaType.APPLICATION_PDF_VALUE;
        if (lower.endsWith(".txt")) return MediaType.TEXT_PLAIN_VALUE;
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    public record StoredFile(String bucket, String objectKey, String originalFilename, long size, String contentType, String url) {}
    public record StoredResource(Resource resource, String contentType, String filename, long size) {}
}
