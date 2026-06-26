package com.hbut.medrisk.dto;

import jakarta.validation.constraints.NotBlank;

public record ModelFeedbackRequest(
        Long modelVersionId,
        Long evaluationId,
        @NotBlank String problemType,
        @NotBlank String priority,
        @NotBlank String status,
        @NotBlank String content) {
}
