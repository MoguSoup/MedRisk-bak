package com.hbut.medrisk.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbut.medrisk.dto.AdminPasswordResetRequest;
import com.hbut.medrisk.dto.AdminUserRequest;
import com.hbut.medrisk.dto.AdminUserResponse;
import com.hbut.medrisk.dto.AuditLogResponse;
import com.hbut.medrisk.dto.ModelVersionResponse;
import com.hbut.medrisk.dto.UserStatusRequest;
import com.hbut.medrisk.entity.AuditLogEntity;
import com.hbut.medrisk.entity.ModelTrainingJobEntity;
import com.hbut.medrisk.entity.ModelVersionEntity;
import com.hbut.medrisk.entity.PredictionRecordEntity;
import com.hbut.medrisk.entity.ReportRecordEntity;
import com.hbut.medrisk.entity.UserEntity;
import com.hbut.medrisk.repository.AuditLogRepository;
import com.hbut.medrisk.repository.ConversationRepository;
import com.hbut.medrisk.repository.DiseaseInfoRepository;
import com.hbut.medrisk.repository.KnowledgeDocumentRepository;
import com.hbut.medrisk.repository.KnowledgeGraphJobRepository;
import com.hbut.medrisk.repository.MedicalCaseRepository;
import com.hbut.medrisk.repository.ModelEvaluationRepository;
import com.hbut.medrisk.repository.ModelFeedbackRepository;
import com.hbut.medrisk.repository.ModelTrainingJobRepository;
import com.hbut.medrisk.repository.ModelVersionRepository;
import com.hbut.medrisk.repository.PredictionRecordRepository;
import com.hbut.medrisk.repository.QaHistoryRepository;
import com.hbut.medrisk.repository.ReportRecordRepository;
import com.hbut.medrisk.repository.TrainingDatasetRepository;
import com.hbut.medrisk.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {
    private static final List<String> ROLES = List.of("PATIENT", "DOCTOR", "ADMIN");
    private static final List<String> STATUSES = List.of("ACTIVE", "DISABLED");

    private final UserRepository users;
    private final ModelVersionRepository models;
    private final AuditLogRepository auditLogs;
    private final PredictionRecordRepository predictions;
    private final ReportRecordRepository reports;
    private final TrainingDatasetRepository datasets;
    private final ModelTrainingJobRepository trainingJobs;
    private final ModelEvaluationRepository evaluations;
    private final ModelFeedbackRepository feedback;
    private final KnowledgeDocumentRepository knowledgeDocuments;
    private final KnowledgeGraphJobRepository graphJobs;
    private final ConversationRepository conversations;
    private final QaHistoryRepository qaHistory;
    private final DiseaseInfoRepository diseases;
    private final MedicalCaseRepository medicalCases;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public AdminService(
            UserRepository users,
            ModelVersionRepository models,
            AuditLogRepository auditLogs,
            PredictionRecordRepository predictions,
            ReportRecordRepository reports,
            TrainingDatasetRepository datasets,
            ModelTrainingJobRepository trainingJobs,
            ModelEvaluationRepository evaluations,
            ModelFeedbackRepository feedback,
            KnowledgeDocumentRepository knowledgeDocuments,
            KnowledgeGraphJobRepository graphJobs,
            ConversationRepository conversations,
            QaHistoryRepository qaHistory,
            DiseaseInfoRepository diseases,
            MedicalCaseRepository medicalCases,
            PasswordEncoder passwordEncoder,
            AuditService auditService,
            ObjectMapper objectMapper) {
        this.users = users;
        this.models = models;
        this.auditLogs = auditLogs;
        this.predictions = predictions;
        this.reports = reports;
        this.datasets = datasets;
        this.trainingJobs = trainingJobs;
        this.evaluations = evaluations;
        this.feedback = feedback;
        this.knowledgeDocuments = knowledgeDocuments;
        this.graphJobs = graphJobs;
        this.conversations = conversations;
        this.qaHistory = qaHistory;
        this.diseases = diseases;
        this.medicalCases = medicalCases;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public List<ModelVersionResponse> models() {
        return models.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream().map(this::toModelResponse).toList();
    }

    public List<AuditLogResponse> auditLogs() {
        return auditLogs.findTop100ByOrderByCreatedAtDesc().stream().map(this::toAuditResponse).toList();
    }

    public List<AdminUserResponse> users(String keyword, String role, String status) {
        String normalizedKeyword = clean(keyword).toLowerCase(Locale.ROOT);
        String normalizedRole = clean(role).toUpperCase(Locale.ROOT);
        String normalizedStatus = clean(status).toUpperCase(Locale.ROOT);
        return users.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .filter(user -> normalizedKeyword.isBlank() || contains(user.getUsername(), normalizedKeyword)
                        || contains(user.getEmail(), normalizedKeyword)
                        || contains(user.getName(), normalizedKeyword)
                        || contains(user.getPhone(), normalizedKeyword))
                .filter(user -> normalizedRole.isBlank() || user.getRole().equals(normalizedRole))
                .filter(user -> normalizedStatus.isBlank() || user.getStatus().equals(normalizedStatus))
                .map(this::toUserResponse)
                .toList();
    }

    @Transactional
    public AdminUserResponse createUser(AdminUserRequest request, UserEntity operator) {
        if (users.existsByUsername(request.username())) {
            throw new IllegalArgumentException("用户名已存在");
        }
        if (users.existsByEmail(request.email())) {
            throw new IllegalArgumentException("邮箱已存在");
        }
        UserEntity user = new UserEntity();
        user.setUsername(request.username().trim());
        user.setEmail(request.email().trim());
        applyEditableUserFields(user, request);
        user.setPasswordHash(passwordEncoder.encode(blank(request.password()) ? "123456" : request.password()));
        var now = java.time.LocalDateTime.now();
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        users.save(user);
        auditService.log(operator.getId(), "CREATE_USER", "USER", user.getId().toString(), "{}");
        return toUserResponse(user);
    }

    @Transactional
    public AdminUserResponse updateUser(Long id, AdminUserRequest request, UserEntity operator) {
        UserEntity user = requireUser(id);
        if (users.existsByUsernameAndIdNot(request.username(), id)) {
            throw new IllegalArgumentException("用户名已存在");
        }
        if (users.existsByEmailAndIdNot(request.email(), id)) {
            throw new IllegalArgumentException("邮箱已存在");
        }
        user.setUsername(request.username().trim());
        user.setEmail(request.email().trim());
        applyEditableUserFields(user, request);
        user.setUpdatedAt(java.time.LocalDateTime.now());
        auditService.log(operator.getId(), "UPDATE_USER", "USER", user.getId().toString(), "{}");
        return toUserResponse(user);
    }

    @Transactional
    public AdminUserResponse updateStatus(Long id, UserStatusRequest request, UserEntity operator) {
        if (Objects.equals(id, operator.getId())) {
            throw new IllegalArgumentException("不能禁用当前登录账号");
        }
        UserEntity user = requireUser(id);
        user.setStatus(normalizeStatus(request.status()));
        user.setUpdatedAt(java.time.LocalDateTime.now());
        auditService.log(operator.getId(), "UPDATE_USER_STATUS", "USER", user.getId().toString(), "{\"status\":\"" + user.getStatus() + "\"}");
        return toUserResponse(user);
    }

    @Transactional
    public Map<String, Object> resetPassword(Long id, AdminPasswordResetRequest request, UserEntity operator) {
        UserEntity user = requireUser(id);
        String password = request == null || blank(request.password()) ? "123456" : request.password();
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setUpdatedAt(java.time.LocalDateTime.now());
        auditService.log(operator.getId(), "RESET_USER_PASSWORD", "USER", user.getId().toString(), "{}");
        return Map.of("id", user.getId(), "defaultPassword", blank(request == null ? null : request.password()));
    }

    @Transactional
    public Map<String, Object> deleteUser(Long id, UserEntity operator) {
        if (Objects.equals(id, operator.getId())) {
            throw new IllegalArgumentException("不能删除当前登录账号");
        }
        UserEntity user = requireUser(id);
        if (hasBusinessRecords(id)) {
            throw new IllegalArgumentException("该用户已有业务记录，请禁用账号而不是删除");
        }
        users.delete(user);
        auditService.log(operator.getId(), "DELETE_USER", "USER", id.toString(), "{}");
        return Map.of("deleted", true, "id", id);
    }

    public Map<String, Object> consoleSummary() {
        List<UserEntity> allUsers = users.findAll();
        List<PredictionRecordEntity> allPredictions = predictions.findAll();
        List<ReportRecordEntity> allReports = reports.findAll();
        List<ModelVersionEntity> allModels = models.findAll();
        List<ModelTrainingJobEntity> allJobs = trainingJobs.findAll();
        Map<String, Long> trainingStatus = countBy(allJobs.stream().map(ModelTrainingJobEntity::getTrainStatus).toList());
        return orderedMap(
                "userCount", allUsers.size(),
                "patientCount", countUsers(allUsers, "PATIENT"),
                "doctorCount", countUsers(allUsers, "DOCTOR"),
                "adminCount", countUsers(allUsers, "ADMIN"),
                "disabledUserCount", allUsers.stream().filter(user -> "DISABLED".equals(user.getStatus())).count(),
                "predictionCount", allPredictions.size(),
                "highRiskCount", allPredictions.stream().filter(row -> "high".equals(row.getRiskLabel())).count(),
                "reportCount", allReports.size(),
                "modelCount", allModels.size(),
                "activeModelCount", allModels.stream().filter(row -> Boolean.TRUE.equals(row.getActive())).count(),
                "datasetCount", datasets.count(),
                "knowledgeDocumentCount", knowledgeDocuments.count(),
                "conversationCount", conversations.count(),
                "qaCount", qaHistory.count(),
                "diseaseInfoCount", diseases.count(),
                "medicalCaseCount", medicalCases.count(),
                "graphJobCount", graphJobs.count(),
                "trainingJobCount", allJobs.size(),
                "runningJobCount", allJobs.stream().filter(row -> !isTrainingDone(row.getTrainStatus())).count(),
                "pendingFeedbackCount", feedback.findAll().stream().filter(row -> !"已解决".equals(row.getStatus())).count(),
                "trainingStatus", toNameValue(trainingStatus),
                "recentAuditLogs", auditLogs().stream().limit(8).toList());
    }

    public Map<String, Object> visualization() {
        List<UserEntity> allUsers = users.findAll();
        List<PredictionRecordEntity> allPredictions = predictions.findAll();
        List<ReportRecordEntity> allReports = reports.findAll();
        List<ModelVersionEntity> activeModels = models.findByActiveTrueOrderByDiseaseTypeAsc();
        return orderedMap(
                "summary", consoleSummary(),
                "riskDistribution", toNameValue(countBy(allPredictions.stream().map(row -> riskText(row.getRiskLabel())).toList())),
                "diseaseDistribution", toNameValue(countBy(allPredictions.stream().map(PredictionRecordEntity::getDiseaseName).toList())),
                "predictionTrend", predictionTrend(allPredictions, allReports),
                "modelMetrics", activeModels.stream().map(this::modelMetricMap).toList(),
                "activeUsers", List.of(
                        orderedMap("name", "患者", "value", countUsers(allUsers, "PATIENT")),
                        orderedMap("name", "医生", "value", countUsers(allUsers, "DOCTOR")),
                        orderedMap("name", "管理员", "value", countUsers(allUsers, "ADMIN"))));
    }

    public Map<String, Object> doctorSummary() {
        List<PredictionRecordEntity> allPredictions = predictions.findTop100ByOrderByCreatedAtDesc();
        return orderedMap(
                "predictionCount", predictions.count(),
                "highRiskCount", allPredictions.stream().filter(row -> "high".equals(row.getRiskLabel())).count(),
                "reportCount", reports.count(),
                "patientCount", allPredictions.stream().map(PredictionRecordEntity::getPatientName).filter(Objects::nonNull).distinct().count(),
                "riskDistribution", toNameValue(countBy(allPredictions.stream().map(row -> riskText(row.getRiskLabel())).toList())),
                "diseaseDistribution", toNameValue(countBy(allPredictions.stream().map(PredictionRecordEntity::getDiseaseName).toList())),
                "recentHighRisk", allPredictions.stream().filter(row -> "high".equals(row.getRiskLabel())).limit(8).map(this::predictionMap).toList());
    }

    private ModelVersionResponse toModelResponse(ModelVersionEntity model) {
        Map<String, Object> metrics = readMap(model.getMetricsJson());
        return new ModelVersionResponse(
                model.getId(),
                model.getDiseaseType(),
                model.getDiseaseName(),
                model.getModelName(),
                model.getModelType() == null || model.getModelType().isBlank() ? "xgboost" : model.getModelType(),
                model.getVersion(),
                metrics,
                readMap(model.getHyperparametersJson()),
                firstNonBlank(model.getEvaluationDatasetName(), string(metrics.get("evaluationDataset"), null)),
                firstNonBlank(model.getEvaluationDatasetSource(), string(metrics.get("datasetSource"), null)),
                firstNonBlank(model.getEvaluationDatasetUrl(), string(metrics.get("datasetUrl"), null)),
                Boolean.TRUE.equals(model.getActive()),
                model.getCreatedAt());
    }

    private AuditLogResponse toAuditResponse(AuditLogEntity log) {
        return new AuditLogResponse(
                log.getId(),
                log.getUserId(),
                log.getAction(),
                log.getResourceType(),
                log.getResourceId(),
                log.getClientIp(),
                log.getDetailJson(),
                log.getCreatedAt());
    }

    private AdminUserResponse toUserResponse(UserEntity user) {
        return new AdminUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getName(),
                user.getPhone(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getLastLoginAt());
    }

    private void applyEditableUserFields(UserEntity user, AdminUserRequest request) {
        user.setName(request.name().trim());
        user.setPhone(blank(request.phone()) ? null : request.phone().trim());
        user.setRole(normalizeRole(request.role()));
        user.setStatus(normalizeStatus(request.status()));
    }

    private UserEntity requireUser(Long id) {
        return users.findById(id).orElseThrow(() -> new EntityNotFoundException("用户不存在"));
    }

    private boolean hasBusinessRecords(Long userId) {
        return predictions.existsByUserId(userId)
                || reports.existsByGeneratedBy(userId)
                || datasets.existsByUploadedBy(userId)
                || trainingJobs.existsByUserId(userId)
                || evaluations.existsByUserId(userId)
                || feedback.existsByUserId(userId)
                || conversations.existsByUserId(userId)
                || qaHistory.existsByUserId(userId);
    }

    private String normalizeRole(String role) {
        String normalized = clean(role).toUpperCase(Locale.ROOT);
        if (!ROLES.contains(normalized)) {
            throw new IllegalArgumentException("角色必须是 PATIENT、DOCTOR 或 ADMIN");
        }
        return normalized;
    }

    private String normalizeStatus(String status) {
        String normalized = blank(status) ? "ACTIVE" : status.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("状态必须是 ACTIVE 或 DISABLED");
        }
        return normalized;
    }

    private long countUsers(List<UserEntity> allUsers, String role) {
        return allUsers.stream().filter(user -> role.equals(user.getRole())).count();
    }

    private Map<String, Long> countBy(List<String> values) {
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.groupingBy(value -> value, LinkedHashMap::new, Collectors.counting()));
    }

    private List<Map<String, Object>> toNameValue(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .map(entry -> orderedMap("name", entry.getKey(), "value", entry.getValue()))
                .toList();
    }

    private List<Map<String, Object>> predictionTrend(List<PredictionRecordEntity> allPredictions, List<ReportRecordEntity> allReports) {
        LocalDate today = LocalDate.now();
        return java.util.stream.IntStream.rangeClosed(0, 6)
                .mapToObj(offset -> today.minusDays(6L - offset))
                .map(date -> orderedMap(
                        "date", date.toString(),
                        "predictions", allPredictions.stream().filter(row -> row.getCreatedAt().toLocalDate().equals(date)).count(),
                        "reports", allReports.stream().filter(row -> row.getCreatedAt().toLocalDate().equals(date)).count()))
                .toList();
    }

    private Map<String, Object> modelMetricMap(ModelVersionEntity model) {
        Map<String, Object> metrics = readMap(model.getMetricsJson());
        return orderedMap(
                "diseaseName", model.getDiseaseName(),
                "modelName", model.getModelName(),
                "modelType", model.getModelType() == null || model.getModelType().isBlank() ? "xgboost" : model.getModelType(),
                "version", model.getVersion(),
                "auc", metrics.get("auc"),
                "recall", metrics.get("recall"),
                "f1", metrics.get("f1"));
    }

    private Map<String, Object> predictionMap(PredictionRecordEntity row) {
        return orderedMap(
                "id", row.getId(),
                "patientName", row.getPatientName(),
                "diseaseName", row.getDiseaseName(),
                "riskLabel", row.getRiskLabel(),
                "riskProbability", row.getRiskProbability(),
                "createdAt", row.getCreatedAt());
    }

    private boolean isTrainingDone(String status) {
        return List.of("训练成功", "训练失败", "训练终止", "评估完成").contains(status);
    }

    private String riskText(String riskLabel) {
        return switch (riskLabel) {
            case "high" -> "高风险";
            case "medium" -> "中风险";
            default -> "低风险";
        };
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String string(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private Map<String, Object> orderedMap(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        }
        return map;
    }

    private Map<String, Object> readMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }
}
