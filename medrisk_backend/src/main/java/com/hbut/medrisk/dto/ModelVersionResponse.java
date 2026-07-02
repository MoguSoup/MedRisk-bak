package com.hbut.medrisk.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record ModelVersionResponse(
        Long id,
        String diseaseType,
        String diseaseName,
        String modelName,
        String modelType,
        String version,
        Map<String, Object> metrics,
        Map<String, Object> hyperparameters,
        String evaluationDatasetName,
        String evaluationDatasetSource,
        String evaluationDatasetUrl,
        boolean active,
        LocalDateTime createdAt) {
}
