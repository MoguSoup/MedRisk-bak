package com.hbut.medrisk.service;

import com.hbut.medrisk.entity.KnowledgeDocumentEntity;
import com.hbut.medrisk.entity.UserEntity;
import com.hbut.medrisk.repository.KnowledgeDocumentRepository;
import jakarta.persistence.EntityNotFoundException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KnowledgeDocumentService {
    private final KnowledgeDocumentRepository documents;
    private final FileStorageService files;
    private final DocumentTextExtractor extractor;
    private final LlmService llm;
    private final AuditService auditService;

    public KnowledgeDocumentService(
            KnowledgeDocumentRepository documents,
            FileStorageService files,
            DocumentTextExtractor extractor,
            LlmService llm,
            AuditService auditService) {
        this.documents = documents;
        this.files = files;
        this.extractor = extractor;
        this.llm = llm;
        this.auditService = auditService;
    }

    public List<Map<String, Object>> list(String keyword, UserEntity user) {
        String normalized = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        return documents.findAllNewest().stream()
                .filter(row -> VisibilityPolicy.canRead(row.getVisibility(), row.getUploadedBy(), user))
                .filter(row -> normalized.isBlank()
                        || contains(row.getTitle(), normalized)
                        || contains(row.getOriginalFileName(), normalized)
                        || contains(row.getContent(), normalized))
                .limit(200)
                .map(this::toMap)
                .toList();
    }

    public Map<String, Object> get(Long id, UserEntity user) {
        KnowledgeDocumentEntity document = requireDocument(id);
        requireReadable(document, user);
        return toMap(document);
    }

    @Transactional
    public Map<String, Object> upload(String title, MultipartFile file, UserEntity user) throws IOException {
        return upload(title, file, user, "PUBLIC");
    }

    @Transactional
    public Map<String, Object> upload(String title, MultipartFile file, UserEntity user, String visibility) throws IOException {
        String content = extractor.extract(file).trim();
        FileStorageService.StoredFile stored = files.store("knowledge-documents", file);
        KnowledgeDocumentEntity document = new KnowledgeDocumentEntity();
        document.setTitle(blank(title) ? stripExtension(stored.originalFilename()) : title.trim());
        document.setOriginalFileName(stored.originalFilename());
        document.setFileType(fileType(stored.originalFilename()));
        document.setFileSize(stored.size());
        document.setFileBucket(stored.bucket());
        document.setFileObjectKey(stored.objectKey());
        document.setContent(content);
        document.setSummary(llm.summarize(document.getTitle(), content));
        document.setGraphStatus("未构建");
        document.setUploadedBy(user.getId());
        document.setUserName(user.getName());
        document.setVisibility(VisibilityPolicy.normalize(visibility, "PUBLIC"));
        LocalDateTime now = LocalDateTime.now();
        document.setCreatedAt(now);
        document.setUpdatedAt(now);
        documents.save(document);
        auditService.log(user.getId(), "UPLOAD_KNOWLEDGE_DOCUMENT", "DOCUMENT", document.getId().toString(), "{}");
        return toMap(document);
    }

    @Transactional
    public Map<String, Object> update(Long id, String title, String summary, String visibility, String sourceName, String sourceUrl, String sourceLicense, UserEntity user) {
        KnowledgeDocumentEntity document = requireDocument(id);
        if (!blank(title)) {
            document.setTitle(title.trim());
        }
        if (summary != null) {
            document.setSummary(summary.trim());
        }
        if (!blank(visibility)) {
            document.setVisibility(VisibilityPolicy.normalize(visibility, document.getVisibility()));
        }
        if (sourceName != null) document.setSourceName(clean(sourceName));
        if (sourceUrl != null) document.setSourceUrl(clean(sourceUrl));
        if (sourceLicense != null) document.setSourceLicense(clean(sourceLicense));
        document.setUpdatedAt(LocalDateTime.now());
        auditService.log(user.getId(), "UPDATE_KNOWLEDGE_DOCUMENT", "DOCUMENT", document.getId().toString(), "{}");
        return toMap(document);
    }

    @Transactional
    public Map<String, Object> delete(Long id, UserEntity user) {
        KnowledgeDocumentEntity document = requireDocument(id);
        documents.delete(document);
        auditService.log(user.getId(), "DELETE_KNOWLEDGE_DOCUMENT", "DOCUMENT", id.toString(), "{}");
        return Map.of("deleted", true, "id", id);
    }

    public FileStorageService.StoredResource download(Long id, UserEntity user) throws IOException {
        KnowledgeDocumentEntity document = requireDocument(id);
        requireReadable(document, user);
        return files.load(document.getFileBucket(), document.getFileObjectKey());
    }

    public KnowledgeDocumentEntity requireDocument(Long id) {
        return documents.findById(id).orElseThrow(() -> new EntityNotFoundException("文档不存在"));
    }

    Map<String, Object> toMap(KnowledgeDocumentEntity row) {
        return orderedMap(
                "id", row.getId(),
                "title", row.getTitle(),
                "originalFileName", row.getOriginalFileName(),
                "fileType", row.getFileType(),
                "fileSize", row.getFileSize(),
                "content", row.getContent(),
                "summary", row.getSummary(),
                "graphStatus", row.getGraphStatus(),
                "graphError", row.getGraphError(),
                "visibility", row.getVisibility(),
                "visibilityLabel", VisibilityPolicy.display(row.getVisibility()),
                "sourceName", row.getSourceName(),
                "sourceUrl", row.getSourceUrl(),
                "sourceLicense", row.getSourceLicense(),
                "sourceRecordId", row.getSourceRecordId(),
                "retrievedAt", row.getRetrievedAt(),
                "uploadedBy", row.getUploadedBy(),
                "userName", row.getUserName(),
                "fileUrl", "/api/files/" + row.getFileBucket() + "/" + row.getFileObjectKey(),
                "createdAt", row.getCreatedAt(),
                "updatedAt", row.getUpdatedAt());
    }

    private String fileType(String filename) {
        int index = filename.lastIndexOf('.');
        return index < 0 ? "unknown" : filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String stripExtension(String filename) {
        int index = filename.lastIndexOf('.');
        return index < 0 ? filename : filename.substring(0, index);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private void requireReadable(KnowledgeDocumentEntity document, UserEntity user) {
        if (!VisibilityPolicy.canRead(document.getVisibility(), document.getUploadedBy(), user)) {
            throw new SecurityException("不能访问该文档");
        }
    }

    private Map<String, Object> orderedMap(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }
}
