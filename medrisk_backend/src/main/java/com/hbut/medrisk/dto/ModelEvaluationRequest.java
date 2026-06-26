package com.hbut.medrisk.dto;

import jakarta.validation.constraints.NotNull;

public record ModelEvaluationRequest(@NotNull Long modelVersionId, @NotNull Long datasetId) {
}
