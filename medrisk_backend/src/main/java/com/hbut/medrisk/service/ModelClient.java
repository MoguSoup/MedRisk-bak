package com.hbut.medrisk.service;

import com.hbut.medrisk.dto.ModelPredictionResponse;
import com.hbut.medrisk.dto.PredictionFactorInfo;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class ModelClient {
    private static final String DISCLAIMER = "本结果仅用于教学演示和健康风险提示，不能替代医生诊断。";

    private final RestTemplate restTemplate;
    private final String modelServiceUrl;

    public ModelClient(RestTemplate restTemplate, @Value("${medrisk.model-service-url}") String modelServiceUrl) {
        this.restTemplate = restTemplate;
        this.modelServiceUrl = modelServiceUrl;
    }

    public ModelPredictionResponse predict(String diseaseType, Map<String, Object> payload) {
        try {
            ModelPredictionResponse response = restTemplate.postForObject(
                    modelServiceUrl + "/predict/" + diseaseType,
                    payload,
                    ModelPredictionResponse.class);
            if (response != null) {
                return response;
            }
        } catch (RestClientException ignored) {
            // Demo fallback keeps the teaching loop usable if the model service is offline.
        }
        return fallback(diseaseType, payload);
    }

    private ModelPredictionResponse fallback(String diseaseType, Map<String, Object> payload) {
        double age = number(payload, "age", 45);
        double bmi = number(payload, "bmi", 24);
        double glucose = number(payload, "glucose", 5.6);
        double bp = number(payload, "bloodPressure", 120);
        double score = clamp((age - 35) / 60 * 0.28 + (bmi - 22) / 18 * 0.22 + (glucose - 5) / 7 * 0.28 + (bp - 115) / 70 * 0.22);
        String label = score >= 0.7 ? "high" : score >= 0.4 ? "medium" : "low";
        String diseaseName = switch (diseaseType) {
            case "diabetes" -> "糖尿病";
            case "heart" -> "心脏病";
            case "kidney" -> "慢性肾病";
            case "liver" -> "肝病";
            case "stroke" -> "中风";
            default -> throw new IllegalArgumentException("暂不支持该病种: " + diseaseType);
        };
        List<PredictionFactorInfo> factors = List.of(
                new PredictionFactorInfo("age", "年龄", age, 0.28, "increase"),
                new PredictionFactorInfo("bmi", "BMI kg/m²", bmi, 0.22, "increase"),
                new PredictionFactorInfo("glucose", "血糖 mmol/L", glucose, 0.28, "increase"),
                new PredictionFactorInfo("bloodPressure", "收缩压 mmHg", bp, 0.22, "increase"));
        return new ModelPredictionResponse(
                diseaseType,
                diseaseName,
                label,
                Math.round(score * 10000.0) / 10000.0,
                0.76,
                diseaseType + "-demo-fallback-v1.0.0",
                factors,
                List.of("建议结合体检结果复查相关指标。", "如出现明显不适，请及时咨询专业医生。"),
                DISCLAIMER);
    }

    private double number(Map<String, Object> payload, String key, double fallback) {
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private double clamp(double value) {
        return Math.max(0.05, Math.min(0.95, value));
    }
}
