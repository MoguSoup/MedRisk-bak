package com.hbut.medrisk.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record TrainingJobResponse(
        Long id,
        Long datasetId,
        String datasetName,
        Long evaluationDatasetId,
        String evaluationDatasetName,
        String evaluationDatasetSource,
        String evaluationDatasetUrl,
        Long userId,
        String diseaseType,
        String modelName,
        String modelType,
        String trainStatus,
        Integer progress,
        Double currentLoss,
        Integer trainEpoch,
        Double learningRate,
        Double testSize,
        Map<String, Object> hyperparameters,
        String modelVersion,
        String modelPath,
        String historyPath,
        String metadataPath,
        Map<String, Object> metrics,
        String message,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
