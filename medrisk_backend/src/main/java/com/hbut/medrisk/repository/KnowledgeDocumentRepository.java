package com.hbut.medrisk.repository;

import com.hbut.medrisk.entity.KnowledgeDocumentEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocumentEntity, Long> {
    List<KnowledgeDocumentEntity> findTop100ByOrderByCreatedAtDesc();
    List<KnowledgeDocumentEntity> findByGraphStatusOrderByCreatedAtAsc(String graphStatus);
    List<KnowledgeDocumentEntity> findByGraphStatusInOrderByCreatedAtAsc(List<String> graphStatuses);
    Optional<KnowledgeDocumentEntity> findBySourceRecordId(String sourceRecordId);
    long countBySourceName(String sourceName);
    default List<KnowledgeDocumentEntity> findAllNewest() {
        return findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
