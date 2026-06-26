package com.hbut.medrisk.repository;

import com.hbut.medrisk.entity.TrainingDatasetEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrainingDatasetRepository extends JpaRepository<TrainingDatasetEntity, Long> {
    List<TrainingDatasetEntity> findTop200ByOrderByCreatedAtDesc();

    boolean existsByUploadedBy(Long uploadedBy);

    Optional<TrainingDatasetEntity> findBySourceRecordId(String sourceRecordId);

    long countBySourceName(String sourceName);
}
