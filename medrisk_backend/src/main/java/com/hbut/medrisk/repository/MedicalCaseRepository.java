package com.hbut.medrisk.repository;

import com.hbut.medrisk.entity.MedicalCaseEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicalCaseRepository extends JpaRepository<MedicalCaseEntity, Long> {
    List<MedicalCaseEntity> findByDiseaseIdOrderByCreatedAtDesc(Long diseaseId);
    boolean existsByDiseaseId(Long diseaseId);
    boolean existsByDataSource(String dataSource);
    Optional<MedicalCaseEntity> findBySourceRecordId(String sourceRecordId);
    long countBySourceName(String sourceName);
    default List<MedicalCaseEntity> newest() {
        return findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
