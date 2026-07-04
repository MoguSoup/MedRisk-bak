package com.hbut.medrisk.repository;

import com.hbut.medrisk.entity.ModelEvaluationEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelEvaluationRepository extends JpaRepository<ModelEvaluationEntity, Long> {
    List<ModelEvaluationEntity> findTop200ByOrderByCreatedAtDesc();

    List<ModelEvaluationEntity> findTop200ByOrderByIdDesc();

    boolean existsByDatasetId(Long datasetId);

    List<ModelEvaluationEntity> findByDatasetId(Long datasetId);

    List<ModelEvaluationEntity> findByModelVersionId(Long modelVersionId);

    boolean existsByUserId(Long userId);
}
