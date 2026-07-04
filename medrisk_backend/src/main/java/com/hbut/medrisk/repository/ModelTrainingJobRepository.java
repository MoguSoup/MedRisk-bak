package com.hbut.medrisk.repository;

import com.hbut.medrisk.entity.ModelTrainingJobEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelTrainingJobRepository extends JpaRepository<ModelTrainingJobEntity, Long> {
    List<ModelTrainingJobEntity> findTop200ByOrderByCreatedAtDesc();

    List<ModelTrainingJobEntity> findTop200ByOrderByIdDesc();

    boolean existsByDatasetId(Long datasetId);

    List<ModelTrainingJobEntity> findByDatasetIdOrEvaluationDatasetId(Long datasetId, Long evaluationDatasetId);

    List<ModelTrainingJobEntity> findByModelVersion(String modelVersion);

    boolean existsByUserId(Long userId);

    long countByTrainStatusNotIn(List<String> statuses);
}
