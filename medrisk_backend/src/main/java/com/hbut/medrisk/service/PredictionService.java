package com.hbut.medrisk.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbut.medrisk.dto.ModelPredictionResponse;
import com.hbut.medrisk.dto.PredictionResponse;
import com.hbut.medrisk.entity.PredictionRecordEntity;
import com.hbut.medrisk.entity.ReportRecordEntity;
import com.hbut.medrisk.entity.UserEntity;
import com.hbut.medrisk.repository.PredictionRecordRepository;
import com.hbut.medrisk.repository.ReportRecordRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PredictionService {
    private final ModelClient modelClient;
    private final PredictionRecordRepository predictions;
    private final ReportRecordRepository reports;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    public PredictionService(
            ModelClient modelClient,
            PredictionRecordRepository predictions,
            ReportRecordRepository reports,
            ObjectMapper objectMapper,
            AuditService auditService) {
        this.modelClient = modelClient;
        this.predictions = predictions;
        this.reports = reports;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    @Transactional
    public PredictionResponse predict(String diseaseType, Map<String, Object> payload, UserEntity user) {
        try {
            ModelPredictionResponse model = modelClient.predict(diseaseType, payload);
            PredictionRecordEntity record = new PredictionRecordEntity();
            record.setUserId(user.getId());
            record.setPatientName(String.valueOf(payload.getOrDefault("patientName", user.getName())));
            record.setDiseaseType(model.diseaseType());
            record.setDiseaseName(model.diseaseName());
            record.setInputJson(objectMapper.writeValueAsString(payload));
            record.setResultJson(objectMapper.writeValueAsString(model));
            record.setRiskLabel(model.riskLabel());
            record.setRiskProbability(model.riskProbability());
            record.setConfidence(model.confidence());
            record.setModelVersion(model.modelVersion());
            record.setCreatedAt(LocalDateTime.now());
            predictions.save(record);
            auditService.log(user.getId(), "CREATE_PREDICTION", "PREDICTION", record.getId().toString(), record.getResultJson());
            return toResponse(record, model);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("预测服务暂时不可用");
        }
    }

    public List<PredictionResponse> history(UserEntity user) {
        List<PredictionRecordEntity> records = "PATIENT".equals(user.getRole())
                ? predictions.findTop100ByUserIdOrderByCreatedAtDesc(user.getId())
                : predictions.findTop100ByOrderByCreatedAtDesc();
        return records.stream().map(this::toResponse).toList();
    }

    public PredictionResponse explain(Long recordId, UserEntity user) {
        PredictionRecordEntity record = requireAccessibleRecord(recordId, user);
        return toResponse(record);
    }

    public PredictionRecordEntity requireAccessibleRecord(Long recordId, UserEntity user) {
        PredictionRecordEntity record = predictions.findById(recordId)
                .orElseThrow(() -> new EntityNotFoundException("预测记录不存在"));
        if ("PATIENT".equals(user.getRole()) && !record.getUserId().equals(user.getId())) {
            throw new SecurityException("不能访问其他患者的预测记录");
        }
        return record;
    }

    @Transactional
    public Map<String, Object> delete(Long recordId, UserEntity user) {
        PredictionRecordEntity record = requireAccessibleRecord(recordId, user);
        List<ReportRecordEntity> linkedReports = reports.findByPredictionIdOrderByCreatedAtDesc(record.getId());
        for (ReportRecordEntity report : linkedReports) {
            reports.delete(report);
            auditService.log(user.getId(), "DELETE_REPORT", "REPORT", report.getId().toString(), "{\"source\":\"prediction-delete\"}");
        }
        predictions.delete(record);
        auditService.log(user.getId(), "DELETE_PREDICTION", "PREDICTION", recordId.toString(), "{\"deletedReports\":" + linkedReports.size() + "}");
        return Map.of("deleted", true, "id", recordId, "deletedReports", linkedReports.size());
    }

    private PredictionResponse toResponse(PredictionRecordEntity record) {
        try {
            ModelPredictionResponse model = objectMapper.readValue(record.getResultJson(), ModelPredictionResponse.class);
            return toResponse(record, model);
        } catch (Exception ex) {
            return new PredictionResponse(
                    record.getId(),
                    record.getDiseaseType(),
                    record.getDiseaseName(),
                    record.getPatientName(),
                    record.getRiskLabel(),
                    record.getRiskProbability(),
                    record.getConfidence(),
                    record.getModelVersion(),
                    List.of(),
                    List.of(),
                    "本结果仅用于教学演示和健康风险提示，不能替代医生诊断。",
                    record.getCreatedAt());
        }
    }

    private PredictionResponse toResponse(PredictionRecordEntity record, ModelPredictionResponse model) {
        return new PredictionResponse(
                record.getId(),
                model.diseaseType(),
                model.diseaseName(),
                record.getPatientName(),
                model.riskLabel(),
                model.riskProbability(),
                model.confidence(),
                model.modelVersion(),
                model.topFactors(),
                model.recommendations(),
                model.disclaimer(),
                record.getCreatedAt());
    }
}
