package com.hbut.medrisk.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record ModelFeedbackResponse(
        Long id,
        Long modelVersionId,
        String modelVersion,
        Long evaluationId,
        Long userId,
        String problemType,
        String priority,
        String status,
        String content,
        Map<String, Object> metricsSnapshot,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
