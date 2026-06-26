package com.hbut.medrisk.repository;

import com.hbut.medrisk.entity.ModelVersionEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelVersionRepository extends JpaRepository<ModelVersionEntity, Long> {
    List<ModelVersionEntity> findByActiveTrueOrderByDiseaseTypeAsc();

    List<ModelVersionEntity> findByDiseaseTypeOrderByCreatedAtDesc(String diseaseType);

    Optional<ModelVersionEntity> findByVersion(String version);
}
