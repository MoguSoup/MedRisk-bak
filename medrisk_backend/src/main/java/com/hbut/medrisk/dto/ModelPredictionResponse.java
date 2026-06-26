package com.hbut.medrisk.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ModelPredictionResponse(
        @JsonProperty("disease_type") String diseaseType,
        @JsonProperty("disease_name") String diseaseName,
        @JsonProperty("risk_label") String riskLabel,
        @JsonProperty("risk_probability") double riskProbability,
        double confidence,
        @JsonProperty("model_version") String modelVersion,
        @JsonProperty("top_factors") List<PredictionFactorInfo> topFactors,
        List<String> recommendations,
        String disclaimer) {
}
