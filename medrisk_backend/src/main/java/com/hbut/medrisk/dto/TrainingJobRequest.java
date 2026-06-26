package com.hbut.medrisk.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TrainingJobRequest(
        @NotNull Long datasetId,
        @NotBlank String modelName,
        @Min(1) @Max(500) Integer epochs,
        Double learningRate,
        Double testSize) {
}
