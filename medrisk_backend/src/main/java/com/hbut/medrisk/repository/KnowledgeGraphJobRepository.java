package com.hbut.medrisk.repository;

import com.hbut.medrisk.entity.KnowledgeGraphJobEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeGraphJobRepository extends JpaRepository<KnowledgeGraphJobEntity, Long> {
    List<KnowledgeGraphJobEntity> findTop50ByOrderByCreatedAtDesc();
    List<KnowledgeGraphJobEntity> findByStatusOrderByCreatedAtDesc(String status);
    boolean existsByStatus(String status);
}
