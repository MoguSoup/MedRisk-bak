package com.hbut.medrisk.repository;

import com.hbut.medrisk.entity.KnowledgeDocumentEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocumentEntity, Long> {
    interface SummaryRow {
        Long getId();
        String getTitle();
        String getOriginalFileName();
        String getFileType();
        Long getFileSize();
        String getSummary();
        String getGraphStatus();
        String getGraphError();
        String getVisibility();
        String getSourceName();
        String getSourceUrl();
        String getSourceLicense();
        String getSourceRecordId();
        LocalDateTime getRetrievedAt();
        Long getUploadedBy();
        String getUserName();
        String getFileBucket();
        String getFileObjectKey();
        LocalDateTime getCreatedAt();
        LocalDateTime getUpdatedAt();
    }

    List<KnowledgeDocumentEntity> findTop100ByOrderByCreatedAtDesc();
    List<KnowledgeDocumentEntity> findByGraphStatusOrderByCreatedAtAsc(String graphStatus);
    List<KnowledgeDocumentEntity> findByGraphStatusInOrderByCreatedAtAsc(List<String> graphStatuses);
    Optional<KnowledgeDocumentEntity> findBySourceRecordId(String sourceRecordId);
    long countBySourceName(String sourceName);
    @Query("""
            SELECT d.id AS id, d.title AS title, d.originalFileName AS originalFileName,
                   d.fileType AS fileType, d.fileSize AS fileSize, d.summary AS summary,
                   d.graphStatus AS graphStatus, d.graphError AS graphError, d.visibility AS visibility,
                   d.sourceName AS sourceName, d.sourceUrl AS sourceUrl, d.sourceLicense AS sourceLicense,
                   d.sourceRecordId AS sourceRecordId, d.retrievedAt AS retrievedAt,
                   d.uploadedBy AS uploadedBy, d.userName AS userName,
                   d.fileBucket AS fileBucket, d.fileObjectKey AS fileObjectKey,
                   d.createdAt AS createdAt, d.updatedAt AS updatedAt
            FROM KnowledgeDocumentEntity d
            ORDER BY d.createdAt DESC
            """)
    List<SummaryRow> findSummaries(Pageable pageable);

    @Query("""
            SELECT d.id AS id, d.title AS title, d.originalFileName AS originalFileName,
                   d.fileType AS fileType, d.fileSize AS fileSize, d.summary AS summary,
                   d.graphStatus AS graphStatus, d.graphError AS graphError, d.visibility AS visibility,
                   d.sourceName AS sourceName, d.sourceUrl AS sourceUrl, d.sourceLicense AS sourceLicense,
                   d.sourceRecordId AS sourceRecordId, d.retrievedAt AS retrievedAt,
                   d.uploadedBy AS uploadedBy, d.userName AS userName,
                   d.fileBucket AS fileBucket, d.fileObjectKey AS fileObjectKey,
                   d.createdAt AS createdAt, d.updatedAt AS updatedAt
            FROM KnowledgeDocumentEntity d
            WHERE LOWER(d.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(d.originalFileName) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(COALESCE(d.sourceName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(COALESCE(d.sourceRecordId, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
            ORDER BY d.createdAt DESC
            """)
    List<SummaryRow> searchSummaries(@Param("keyword") String keyword, Pageable pageable);

    default List<KnowledgeDocumentEntity> findAllNewest() {
        return findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
