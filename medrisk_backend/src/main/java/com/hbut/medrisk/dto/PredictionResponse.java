package com.hbut.medrisk.dto;

import java.time.LocalDateTime;
import java.util.List;

public record PredictionResponse(
        Long recordId,
        String diseaseType,
        String diseaseName,
        String patientName,
        String riskLabel,
        double riskProbability,
        double confidence,
        String modelVersion,
        List<PredictionFactorInfo> topFactors,
        List<String> recommendations,
        String disclaimer,
        LocalDateTime createdAt) {
}
