package com.hbut.medrisk.repository;

import com.hbut.medrisk.entity.ReportRecordEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRecordRepository extends JpaRepository<ReportRecordEntity, Long> {
    List<ReportRecordEntity> findTop100ByGeneratedByOrderByCreatedAtDesc(Long generatedBy);

    List<ReportRecordEntity> findTop100ByOrderByCreatedAtDesc();

    boolean existsByGeneratedBy(Long generatedBy);
}
