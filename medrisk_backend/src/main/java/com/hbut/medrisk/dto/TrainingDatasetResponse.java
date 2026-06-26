package com.hbut.medrisk.dto;

import java.time.LocalDateTime;
import java.util.List;

public record TrainingDatasetResponse(
        Long id,
        String name,
        String diseaseType,
        String description,
        String fileName,
        String filePath,
        String fileType,
        String status,
        Integer sampleCount,
        List<String> featureColumns,
        String validationMessage,
        Long uploadedBy,
        String sourceName,
        String sourceUrl,
        String sourceLicense,
        String sourceRecordId,
        String visibility,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
