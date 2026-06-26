package com.hbut.medrisk.repository;

import com.hbut.medrisk.entity.ModelEvaluationEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelEvaluationRepository extends JpaRepository<ModelEvaluationEntity, Long> {
    List<ModelEvaluationEntity> findTop200ByOrderByCreatedAtDesc();

    boolean existsByDatasetId(Long datasetId);

    boolean existsByUserId(Long userId);
}
