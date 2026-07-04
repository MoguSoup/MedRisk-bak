package com.hbut.medrisk.repository;

import com.hbut.medrisk.entity.ModelFeedbackEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelFeedbackRepository extends JpaRepository<ModelFeedbackEntity, Long> {
    List<ModelFeedbackEntity> findTop200ByOrderByCreatedAtDesc();

    List<ModelFeedbackEntity> findTop200ByOrderByIdDesc();

    List<ModelFeedbackEntity> findByEvaluationIdIn(List<Long> evaluationIds);

    List<ModelFeedbackEntity> findByModelVersionId(Long modelVersionId);

    boolean existsByUserId(Long userId);

    long countByStatusNot(String status);
}
