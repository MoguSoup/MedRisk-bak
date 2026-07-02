package com.hbut.medrisk.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record TrainingJobRequest(
        @NotNull Long datasetId,
        Long evaluationDatasetId,
        @NotBlank String modelName,
        String modelType,
        @Min(1) @Max(500) Integer epochs,
        Double learningRate,
        Double testSize,
        Map<String, Object> hyperparameters) {
}
