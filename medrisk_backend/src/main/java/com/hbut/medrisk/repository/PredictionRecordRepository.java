package com.hbut.medrisk.repository;

import com.hbut.medrisk.entity.PredictionRecordEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PredictionRecordRepository extends JpaRepository<PredictionRecordEntity, Long> {
    List<PredictionRecordEntity> findTop100ByUserIdOrderByCreatedAtDesc(Long userId);

    List<PredictionRecordEntity> findTop100ByOrderByCreatedAtDesc();

    boolean existsByUserId(Long userId);
}
