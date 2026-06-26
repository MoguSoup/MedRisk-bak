package com.hbut.medrisk.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record ModelEvaluationResponse(
        Long id,
        Long modelVersionId,
        String modelVersion,
        Long datasetId,
        String datasetName,
        Map<String, Object> metrics,
        List<Map<String, Object>> predictions,
        LocalDateTime createdAt) {
}
