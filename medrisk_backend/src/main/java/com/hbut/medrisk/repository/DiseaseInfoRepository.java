package com.hbut.medrisk.repository;

import com.hbut.medrisk.entity.DiseaseInfoEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiseaseInfoRepository extends JpaRepository<DiseaseInfoEntity, Long> {
    boolean existsByDiseaseCode(String diseaseCode);
    boolean existsByDiseaseCodeAndIdNot(String diseaseCode, Long id);
    Optional<DiseaseInfoEntity> findByDiseaseCode(String diseaseCode);
    Optional<DiseaseInfoEntity> findBySourceRecordId(String sourceRecordId);
    long countBySourceName(String sourceName);
    List<DiseaseInfoEntity> findTop20ByDiseaseNameContainingIgnoreCaseOrDiseaseCodeContainingIgnoreCaseOrderByCreatedAtDesc(String name, String code);
    default List<DiseaseInfoEntity> newest() {
        return findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
