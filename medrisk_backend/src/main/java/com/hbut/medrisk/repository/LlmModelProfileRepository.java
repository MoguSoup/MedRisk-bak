package com.hbut.medrisk.repository;

import com.hbut.medrisk.entity.LlmModelProfileEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LlmModelProfileRepository extends JpaRepository<LlmModelProfileEntity, Long> {
    List<LlmModelProfileEntity> findByEnabledTrueOrderByDefaultProfileDescUpdatedAtDesc();
    List<LlmModelProfileEntity> findAllByOrderByDefaultProfileDescUpdatedAtDesc();
    Optional<LlmModelProfileEntity> findFirstByDefaultProfileTrueAndEnabledTrueOrderByUpdatedAtDesc();
    boolean existsByModelNameAndBaseUrl(String modelName, String baseUrl);
}
