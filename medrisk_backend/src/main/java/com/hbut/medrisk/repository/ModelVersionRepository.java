package com.hbut.medrisk.repository;

import com.hbut.medrisk.entity.ModelVersionEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelVersionRepository extends JpaRepository<ModelVersionEntity, Long> {
    List<ModelVersionEntity> findTop200ByOrderByIdDesc();

    List<ModelVersionEntity> findByActiveTrueOrderByDiseaseTypeAsc();

    List<ModelVersionEntity> findByDiseaseTypeOrderByCreatedAtDesc(String diseaseType);

    List<ModelVersionEntity> findByVersionIn(Collection<String> versions);

    Optional<ModelVersionEntity> findByVersion(String version);

    long countByActiveTrue();
}
