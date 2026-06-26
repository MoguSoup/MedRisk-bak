package com.hbut.medrisk.repository;

import com.hbut.medrisk.entity.ModelTrainingJobEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelTrainingJobRepository extends JpaRepository<ModelTrainingJobEntity, Long> {
    List<ModelTrainingJobEntity> findTop200ByOrderByCreatedAtDesc();

    boolean existsByDatasetId(Long datasetId);

    boolean existsByUserId(Long userId);
}
