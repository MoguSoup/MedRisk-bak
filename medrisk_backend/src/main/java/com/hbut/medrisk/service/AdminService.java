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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {
    private static final List<String> ROLES = List.of("PATIENT", "DOCTOR", "ADMIN");
    private static final List<String> STATUSES = List.of("ACTIVE", "DISABLED");
    private static final List<String> TRAINING_TERMINAL_STATUSES = List.of("训练成功", "训练失败", "训练终止", "评估完成");
    private static final List<String> MODEL_READY_STATUSES = List.of("训练成功", "评估完成");
    private static final String PROTECTED_ADMIN_USERNAME = "admin";
    private static final int MAX_METRIC_CURVE_POINTS = 400;

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
    private final Path trainingRoot;

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
            ObjectMapper objectMapper,
            @Value("${medrisk.training-output-dir}") String trainingOutputDir) {
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
        this.trainingRoot = Path.of(trainingOutputDir).toAbsolutePath().normalize();
    }

    @Transactional
    public List<ModelVersionResponse> models() {
        ensureSuccessfulTrainingModelsRegistered();
        return models.findTop200ByOrderByIdDesc().stream().map(this::toModelResponse).toList();
    }

    public List<AuditLogResponse> auditLogs() {
        return auditLogs.findTop100ByOrderByCreatedAtDesc().stream().map(this::toAuditResponse).toList();
    }

    public List<AdminUserResponse> users(String keyword, String role, String status) {
        String normalizedKeyword = clean(keyword).toLowerCase(Locale.ROOT);
        String normalizedRole = clean(role).toUpperCase(Locale.ROOT);
        String normalizedStatus = clean(status).toUpperCase(Locale.ROOT);
        return users.findTop200ByOrderByCreatedAtDesc().stream()
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
        String username = clean(request.username());
        String email = clean(request.email());
        if (users.existsByUsername(username)) {
            throw new IllegalArgumentException("用户名已存在");
        }
        if (users.existsByEmail(email)) {
            throw new IllegalArgumentException("邮箱已存在");
        }
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setEmail(email);
        applyEditableUserFields(user, request);
        String password = clean(request.password());
        user.setPasswordHash(passwordEncoder.encode(password.isBlank() ? "123456" : password));
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
        String username = clean(request.username());
        String email = clean(request.email());
        String nextRole = normalizeRole(request.role());
        String nextStatus = normalizeStatus(request.status());
        if (isProtectedAdmin(user)) {
            if (!PROTECTED_ADMIN_USERNAME.equalsIgnoreCase(username)) {
                throw new IllegalArgumentException("演示管理员账号名不能修改");
            }
            if (!"ADMIN".equals(nextRole)) {
                throw new IllegalArgumentException("演示管理员不能降级");
            }
            if (!"ACTIVE".equals(nextStatus)) {
                throw new IllegalArgumentException("演示管理员不能禁用");
            }
        }
        if (Objects.equals(id, operator.getId()) && !"ACTIVE".equals(nextStatus)) {
            throw new IllegalArgumentException("不能禁用当前登录账号");
        }
        if (Objects.equals(id, operator.getId()) && !"ADMIN".equals(nextRole)) {
            throw new IllegalArgumentException("不能修改当前登录账号的管理员身份");
        }
        if (users.existsByUsernameAndIdNot(username, id)) {
            throw new IllegalArgumentException("用户名已存在");
        }
        if (users.existsByEmailAndIdNot(email, id)) {
            throw new IllegalArgumentException("邮箱已存在");
        }
        user.setUsername(username);
        user.setEmail(email);
        applyEditableUserFields(user, request, nextRole, nextStatus);
        user.setUpdatedAt(java.time.LocalDateTime.now());
        auditService.log(operator.getId(), "UPDATE_USER", "USER", user.getId().toString(), "{}");
        return toUserResponse(user);
    }

    @Transactional
    public AdminUserResponse updateStatus(Long id, UserStatusRequest request, UserEntity operator) {
        UserEntity user = requireUser(id);
        if (isProtectedAdmin(user)) {
            throw new IllegalArgumentException("演示管理员不能禁用");
        }
        if (Objects.equals(id, operator.getId())) {
            throw new IllegalArgumentException("不能禁用当前登录账号");
        }
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
        if (isProtectedAdmin(user)) {
            throw new IllegalArgumentException("演示管理员不能删除");
        }
        if (hasBusinessRecords(id)) {
            throw new IllegalArgumentException("该用户已有业务记录，请禁用账号而不是删除");
        }
        users.delete(user);
        auditService.log(operator.getId(), "DELETE_USER", "USER", id.toString(), "{}");
        return Map.of("deleted", true, "id", id);
    }

    public Map<String, Object> consoleSummary() {
        List<ModelTrainingJobEntity> recentJobs = trainingJobs.findTop200ByOrderByIdDesc();
        Map<String, Long> trainingStatus = countBy(recentJobs.stream().map(ModelTrainingJobEntity::getTrainStatus).toList());
        return orderedMap(
                "userCount", users.count(),
                "patientCount", users.countByRole("PATIENT"),
                "doctorCount", users.countByRole("DOCTOR"),
                "adminCount", users.countByRole("ADMIN"),
                "disabledUserCount", users.countByStatus("DISABLED"),
                "predictionCount", predictions.count(),
                "highRiskCount", predictions.countByRiskLabel("high"),
                "reportCount", reports.count(),
                "modelCount", models.count(),
                "activeModelCount", models.countByActiveTrue(),
                "datasetCount", datasets.count(),
                "knowledgeDocumentCount", knowledgeDocuments.count(),
                "conversationCount", conversations.count(),
                "qaCount", qaHistory.count(),
                "diseaseInfoCount", diseases.count(),
                "medicalCaseCount", medicalCases.count(),
                "graphJobCount", graphJobs.count(),
                "trainingJobCount", trainingJobs.count(),
                "runningJobCount", trainingJobs.countByTrainStatusNotIn(TRAINING_TERMINAL_STATUSES),
                "pendingFeedbackCount", feedback.countByStatusNot("已解决"),
                "trainingStatus", toNameValue(trainingStatus),
                "recentAuditLogs", auditLogs().stream().limit(8).toList());
    }

    public Map<String, Object> visualization() {
        List<PredictionRecordEntity> recentPredictions = predictions.findTop100ByOrderByCreatedAtDesc();
        List<ReportRecordEntity> recentReports = reports.findTop100ByOrderByCreatedAtDesc();
        List<ModelVersionEntity> activeModels = models.findByActiveTrueOrderByDiseaseTypeAsc();
        return orderedMap(
                "summary", consoleSummary(),
                "riskDistribution", toNameValue(countBy(recentPredictions.stream().map(row -> riskText(row.getRiskLabel())).toList())),
                "diseaseDistribution", toNameValue(countBy(recentPredictions.stream().map(PredictionRecordEntity::getDiseaseName).toList())),
                "predictionTrend", predictionTrend(recentPredictions, recentReports),
                "modelMetrics", activeModels.stream().map(this::modelMetricMap).toList(),
                "activeUsers", List.of(
                        orderedMap("name", "患者", "value", users.countByRole("PATIENT")),
                        orderedMap("name", "医生", "value", users.countByRole("DOCTOR")),
                        orderedMap("name", "管理员", "value", users.countByRole("ADMIN"))));
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
        Map<String, Object> metrics = readMetricsMap(model.getMetricsJson());
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
                model.getModelPath(),
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
        applyEditableUserFields(user, request, normalizeRole(request.role()), normalizeStatus(request.status()));
    }

    private void applyEditableUserFields(UserEntity user, AdminUserRequest request, String role, String status) {
        user.setName(clean(request.name()));
        user.setPhone(blank(request.phone()) ? null : request.phone().trim());
        user.setRole(role);
        user.setStatus(status);
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
        Map<String, Object> metrics = readMetricsMap(model.getMetricsJson());
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
        return TRAINING_TERMINAL_STATUSES.contains(status);
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

    private void ensureSuccessfulTrainingModelsRegistered() {
        List<ModelTrainingJobEntity> successfulJobs = trainingJobs.findTop200ByOrderByIdDesc().stream()
                .filter(this::isModelReadyJob)
                .map(this::recoverTrainingArtifactFields)
                .filter(job -> !blank(job.getModelVersion()))
                .toList();
        if (successfulJobs.isEmpty()) return;
        Set<String> versions = successfulJobs.stream().map(ModelTrainingJobEntity::getModelVersion).collect(Collectors.toSet());
        Map<String, ModelVersionEntity> existingByVersion = models.findByVersionIn(versions).stream()
                .collect(Collectors.toMap(
                        ModelVersionEntity::getVersion,
                        item -> item,
                        (first, second) -> first,
                        LinkedHashMap::new));
        Set<String> existingVersions = new HashSet<>(existingByVersion.keySet());
        List<ModelVersionEntity> missing = new ArrayList<>();
        for (ModelTrainingJobEntity job : successfulJobs) {
            ModelVersionEntity existing = existingByVersion.get(job.getModelVersion());
            if (existing != null) {
                patchModelVersionFromJob(existing, job);
                continue;
            }
            if (!existingVersions.add(job.getModelVersion())) continue;
            ModelVersionEntity version = new ModelVersionEntity();
            version.setDiseaseType(job.getDiseaseType());
            version.setDiseaseName(diseaseName(job.getDiseaseType()));
            version.setModelName(job.getModelName());
            version.setModelType(blank(job.getModelType()) ? "xgboost" : job.getModelType());
            version.setVersion(job.getModelVersion());
            version.setMetricsJson(blank(job.getMetricsJson()) ? "{}" : writeCompactMetrics(readMap(job.getMetricsJson())));
            version.setFeatureSchemaJson("[]");
            version.setHyperparametersJson(job.getHyperparametersJson());
            version.setModelPath(job.getModelPath());
            version.setActive(false);
            version.setCreatedAt(job.getUpdatedAt() == null ? job.getCreatedAt() : job.getUpdatedAt());
            missing.add(version);
        }
        if (!missing.isEmpty()) {
            models.saveAll(missing);
        }
    }

    private void patchModelVersionFromJob(ModelVersionEntity version, ModelTrainingJobEntity job) {
        boolean changed = false;
        if (blank(version.getModelPath()) && !blank(job.getModelPath())) {
            version.setModelPath(job.getModelPath());
            changed = true;
        }
        if (blank(version.getMetricsJson()) && !blank(job.getMetricsJson())) {
            version.setMetricsJson(writeCompactMetrics(readMap(job.getMetricsJson())));
            changed = true;
        }
        if (blank(version.getHyperparametersJson()) && !blank(job.getHyperparametersJson())) {
            version.setHyperparametersJson(job.getHyperparametersJson());
            changed = true;
        }
        if (blank(version.getModelType()) && !blank(job.getModelType())) {
            version.setModelType(job.getModelType());
            changed = true;
        }
        if (changed) {
            models.save(version);
        }
    }

    private boolean isModelReadyJob(ModelTrainingJobEntity job) {
        return MODEL_READY_STATUSES.contains(job.getTrainStatus())
                || !blank(job.getModelVersion())
                || !blank(job.getModelPath())
                || Files.exists(jobMetadataPath(job))
                || Files.exists(jobModelPath(job));
    }

    private ModelTrainingJobEntity recoverTrainingArtifactFields(ModelTrainingJobEntity job) {
        Map<String, Object> metadata = readJsonMap(firstExistingPath(
                pathOrNull(job.getMetadataPath()),
                jobMetadataPath(job)));
        String version = firstNonBlank(
                job.getModelVersion(),
                string(metadata.get("version"), null));
        Path modelPath = firstExistingPath(
                pathOrNull(job.getModelPath()),
                metadata.isEmpty() ? null : jobMetadataPath(job).getParent().resolve("model.joblib"),
                jobModelPath(job));
        boolean changed = false;
        if (blank(job.getModelVersion()) && !blank(version)) {
            job.setModelVersion(version);
            changed = true;
        }
        if (blank(job.getModelPath()) && modelPath != null) {
            job.setModelPath(modelPath.toString());
            changed = true;
        }
        if (blank(job.getMetadataPath()) && Files.exists(jobMetadataPath(job))) {
            job.setMetadataPath(jobMetadataPath(job).toString());
            changed = true;
        }
        if (blank(job.getMetricsJson()) && metadata.get("metrics") instanceof Map<?, ?> metrics) {
            job.setMetricsJson(writeCompactMetrics(metrics));
            changed = true;
        }
        if (blank(job.getModelType()) && metadata.get("modelType") != null) {
            job.setModelType(String.valueOf(metadata.get("modelType")));
            changed = true;
        }
        if (blank(job.getDiseaseType()) && metadata.get("diseaseType") != null) {
            job.setDiseaseType(String.valueOf(metadata.get("diseaseType")));
            changed = true;
        }
        if (blank(job.getModelName()) && metadata.get("modelName") != null) {
            job.setModelName(String.valueOf(metadata.get("modelName")));
            changed = true;
        }
        if (MODEL_READY_STATUSES.contains(job.getTrainStatus()) && blank(job.getModelVersion()) && !blank(version)) {
            job.setModelVersion(version);
            changed = true;
        }
        if (changed) {
            trainingJobs.save(job);
        }
        return job;
    }

    private Path jobMetadataPath(ModelTrainingJobEntity job) {
        return trainingRoot.resolve("job-" + job.getId()).resolve("metadata.json").normalize();
    }

    private Path jobModelPath(ModelTrainingJobEntity job) {
        return trainingRoot.resolve("job-" + job.getId()).resolve("model.joblib").normalize();
    }

    private Path pathOrNull(String path) {
        return blank(path) ? null : Path.of(path).toAbsolutePath().normalize();
    }

    private Path firstExistingPath(Path... paths) {
        for (Path path : paths) {
            if (path != null && Files.exists(path)) {
                return path;
            }
        }
        return null;
    }

    private Map<String, Object> readJsonMap(Path path) {
        if (path == null || !Files.exists(path)) return Map.of();
        try {
            return objectMapper.readValue(Files.readString(path), new TypeReference<>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String writeObject(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private boolean isProtectedAdmin(UserEntity user) {
        return user != null && PROTECTED_ADMIN_USERNAME.equalsIgnoreCase(clean(user.getUsername()));
    }

    private String diseaseName(String diseaseType) {
        return switch (diseaseType) {
            case "diabetes" -> "糖尿病";
            case "heart" -> "心脏病";
            case "kidney" -> "慢性肾病";
            case "liver" -> "肝病";
            case "stroke" -> "中风";
            default -> diseaseType;
        };
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
            return json == null || json.isBlank() ? Map.of() : objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private Map<String, Object> readMetricsMap(String json) {
        return compactMetrics(readMap(json));
    }

    private String writeCompactMetrics(Object payload) {
        if (payload == null) {
            return "{}";
        }
        try {
            if (payload instanceof String text) {
                return writeObject(readMetricsMap(text));
            }
            Map<String, Object> metrics = objectMapper.convertValue(payload, new TypeReference<>() {});
            return writeObject(compactMetrics(metrics));
        } catch (Exception ex) {
            return "{}";
        }
    }

    private Map<String, Object> compactMetrics(Map<String, Object> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> compacted = new LinkedHashMap<>(metrics);
        compactCurve(compacted, "rocCurve");
        compactCurve(compacted, "prCurve");
        return compacted;
    }

    private void compactCurve(Map<String, Object> metrics, String key) {
        Object value = metrics.get(key);
        if (!(value instanceof List<?> curve) || curve.size() <= MAX_METRIC_CURVE_POINTS) {
            return;
        }
        List<Object> sampled = new ArrayList<>(MAX_METRIC_CURVE_POINTS);
        int previousIndex = -1;
        for (int i = 0; i < MAX_METRIC_CURVE_POINTS; i++) {
            int index = (int) Math.round((double) i * (curve.size() - 1) / (MAX_METRIC_CURVE_POINTS - 1));
            if (index != previousIndex) {
                sampled.add(curve.get(index));
                previousIndex = index;
            }
        }
        metrics.put(key, sampled);
    }
}
