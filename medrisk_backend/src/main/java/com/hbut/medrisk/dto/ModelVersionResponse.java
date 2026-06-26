package com.hbut.medrisk.dto;

import java.time.LocalDateTime;
import java.util.Map;

public record ModelVersionResponse(
        Long id,
        String diseaseType,
        String diseaseName,
        String modelName,
        String version,
        Map<String, Object> metrics,
        boolean active,
        LocalDateTime createdAt) {
}
