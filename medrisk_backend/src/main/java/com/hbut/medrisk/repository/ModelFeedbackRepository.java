package com.hbut.medrisk.repository;

import com.hbut.medrisk.entity.ModelFeedbackEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelFeedbackRepository extends JpaRepository<ModelFeedbackEntity, Long> {
    List<ModelFeedbackEntity> findTop200ByOrderByCreatedAtDesc();

    boolean existsByUserId(Long userId);
}
