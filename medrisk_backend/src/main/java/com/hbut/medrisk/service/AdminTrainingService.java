package com.hbut.medrisk.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbut.medrisk.dto.ModelEvaluationRequest;
import com.hbut.medrisk.dto.ModelEvaluationResponse;
import com.hbut.medrisk.dto.ModelFeedbackRequest;
import com.hbut.medrisk.dto.ModelFeedbackResponse;
import com.hbut.medrisk.dto.ModelVersionResponse;
import com.hbut.medrisk.dto.TrainingDatasetResponse;
import com.hbut.medrisk.dto.TrainingHistoryResponse;
import com.hbut.medrisk.dto.TrainingJobRequest;
import com.hbut.medrisk.dto.TrainingJobResponse;
import com.hbut.medrisk.entity.ModelEvaluationEntity;
import com.hbut.medrisk.entity.ModelFeedbackEntity;
import com.hbut.medrisk.entity.ModelTrainingJobEntity;
import com.hbut.medrisk.entity.ModelVersionEntity;
import com.hbut.medrisk.entity.TrainingDatasetEntity;
import com.hbut.medrisk.entity.UserEntity;
import com.hbut.medrisk.repository.ModelEvaluationRepository;
import com.hbut.medrisk.repository.ModelFeedbackRepository;
import com.hbut.medrisk.repository.ModelTrainingJobRepository;
import com.hbut.medrisk.repository.ModelVersionRepository;
import com.hbut.medrisk.repository.TrainingDatasetRepository;
import jakarta.persistence.EntityNotFoundException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AdminTrainingService {
    private static final List<String> TERMINAL_STATUS = List.of("训练成功", "训练失败", "训练终止", "评估完成");
    private static final List<String> MODEL_TYPES = List.of(
            "xgboost",
            "logistic_regression",
            "random_forest",
            "extra_trees",
            "hist_gradient_boosting",
            "lightgbm",
            "catboost",
            "tabpfn",
            "tabicl",
            "ft_transformer");
    private static final int SMALL_DATASET_THRESHOLD = 1000;
    private static final int MAX_METRIC_CURVE_POINTS = 400;

    private final TrainingDatasetRepository datasets;
    private final ModelTrainingJobRepository jobs;
    private final ModelEvaluationRepository evaluations;
    private final ModelFeedbackRepository feedbacks;
    private final ModelVersionRepository modelVersions;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private final Path uploadRoot;
    private final Path trainingRoot;
    private final Path publicDatasetRoot;
    private final String modelServiceUrl;

    public AdminTrainingService(
            TrainingDatasetRepository datasets,
            ModelTrainingJobRepository jobs,
            ModelEvaluationRepository evaluations,
            ModelFeedbackRepository feedbacks,
            ModelVersionRepository modelVersions,
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            AuditService auditService,
            @Value("${medrisk.upload-dir}") String uploadDir,
            @Value("${medrisk.training-output-dir}") String trainingOutputDir,
            @Value("${medrisk.public-dataset-dir:data/processed}") String publicDatasetDir,
            @Value("${medrisk.model-service-url}") String modelServiceUrl) {
        this.datasets = datasets;
        this.jobs = jobs;
        this.evaluations = evaluations;
        this.feedbacks = feedbacks;
        this.modelVersions = modelVersions;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
        this.trainingRoot = Path.of(trainingOutputDir).toAbsolutePath().normalize();
        this.publicDatasetRoot = Path.of(publicDatasetDir).toAbsolutePath().normalize();
        this.modelServiceUrl = modelServiceUrl;
    }

    public List<TrainingDatasetResponse> listDatasets() {
        return datasets.findTop200ByOrderByCreatedAtDesc().stream().map(this::toDatasetResponse).toList();
    }

    @Transactional
    public List<TrainingDatasetResponse> importPublicDatasets(UserEntity user) {
        Path manifestPath = publicDatasetRoot.resolve("manifest.json").normalize();
        if (!Files.exists(manifestPath)) {
            throw new IllegalStateException("未找到公开数据集 manifest，请先运行 scripts/prepare-large-datasets.ps1");
        }
        try {
            Map<String, Object> manifest = objectMapper.readValue(manifestPath.toFile(), new TypeReference<>() {});
            Object rawDatasets = manifest.get("datasets");
            if (!(rawDatasets instanceof Map<?, ?> datasetMap) || datasetMap.isEmpty()) {
                throw new IllegalStateException("公开数据集 manifest 中没有 datasets");
            }
            List<TrainingDatasetResponse> imported = new ArrayList<>();
            for (Map.Entry<?, ?> entry : datasetMap.entrySet()) {
                if (!(entry.getValue() instanceof Map<?, ?> rawInfo)) continue;
                Map<String, Object> info = new LinkedHashMap<>();
                rawInfo.forEach((key, value) -> info.put(String.valueOf(key), value));
                if (isSmallPublicManifestDataset(info)) {
                    continue;
                }
                String diseaseType = string(info.get("diseaseType"), String.valueOf(entry.getKey()));
                imported.add(toDatasetResponse(upsertPublicDataset(user, diseaseType, info, "train")));
                if (info.containsKey("evaluationFile")) {
                    imported.add(toDatasetResponse(upsertPublicDataset(user, diseaseType, info, "evaluation")));
                }
            }
            auditService.log(user.getId(), "IMPORT_PUBLIC_DATASETS", "TRAINING_DATASET", "PUBLIC", "{}");
            return imported;
        } catch (IOException ex) {
            throw new IllegalStateException("读取公开数据集 manifest 失败: " + ex.getMessage());
        }
    }

    private TrainingDatasetEntity upsertPublicDataset(UserEntity user, String diseaseType, Map<String, Object> info, String split) {
        Path csvPath = resolvePublicDatasetPath(diseaseType, info, split);
        String datasetId = string(info.get("datasetId"), diseaseType);
        String sourceRecordId = "public-dataset:" + datasetId + ":" + split;
        TrainingDatasetEntity row = datasets.findBySourceRecordId(sourceRecordId).orElseGet(TrainingDatasetEntity::new);
        LocalDateTime now = LocalDateTime.now();
        boolean creating = row.getId() == null;
        row.setName(publicDatasetName(diseaseType, string(info.get("displayName"), diseaseType), split));
        row.setDiseaseType(diseaseType);
        row.setDescription("公开数据集自动导入；标签规则：" + string(info.get("labelRule"), "见 manifest"));
        row.setFileName(csvPath.getFileName().toString());
        row.setFilePath(csvPath.toString());
        row.setFileType("csv");
        row.setStatus("UPLOADED");
        row.setUploadedBy(user.getId());
        row.setSourceName(string(info.get("sourceName"), string(info.get("source"), "Public dataset")));
        row.setSourceUrl(string(info.get("sourceUrl"), ""));
        row.setSourceLicense(string(info.get("sourceLicense"), "Public-use data"));
        row.setSourceRecordId(sourceRecordId);
        row.setVisibility("ADMIN_ONLY");
        if (creating) {
            row.setCreatedAt(now);
        }
        row.setUpdatedAt(now);
        applyValidation(row);
        return datasets.save(row);
    }

    private Path resolvePublicDatasetPath(String diseaseType, Map<String, Object> info, String split) {
        String key = "evaluation".equals(split) ? "evaluationFile" : "trainFile";
        String fallbackName = "evaluation".equals(split) ? diseaseType + "_eval.csv" : diseaseType + "_train.csv";
        String configured = string(info.get(key), "");
        Path candidate = configured.isBlank() ? publicDatasetRoot.resolve(fallbackName) : Path.of(configured);
        if (!Files.exists(candidate)) {
            candidate = publicDatasetRoot.resolve(fileNameOnly(configured, fallbackName));
        }
        if (!Files.exists(candidate) && !"evaluation".equals(split)) {
            candidate = publicDatasetRoot.resolve(diseaseType + ".csv");
        }
        if (!Files.exists(candidate)) {
            throw new IllegalStateException("公开数据集文件不存在: " + candidate);
        }
        return candidate.toAbsolutePath().normalize();
    }

    private String fileNameOnly(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String normalized = value.replace("\\", "/");
        int index = normalized.lastIndexOf('/');
        return index >= 0 ? normalized.substring(index + 1) : normalized;
    }

    private String publicDatasetName(String diseaseType, String displayName, String split) {
        String base = displayName == null || displayName.isBlank() || displayName.equals(diseaseType) ? diseaseName(diseaseType) : displayName;
        return base + ("evaluation".equals(split) ? "公开评估集" : "公开训练集");
    }

    @Transactional
    public TrainingDatasetResponse uploadDataset(String name, String diseaseType, String description, MultipartFile file, UserEntity user) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("数据集文件不能为空");
        }
        String original = Objects.requireNonNullElse(file.getOriginalFilename(), "dataset.csv");
        String ext = extension(original);
        if (!List.of("csv", "zip").contains(ext)) {
            throw new IllegalArgumentException("仅支持 csv 或 zip 数据集");
        }
        LocalDateTime now = LocalDateTime.now();
        TrainingDatasetEntity dataset = new TrainingDatasetEntity();
        dataset.setName(name);
        dataset.setDiseaseType(diseaseType);
        dataset.setDescription(description);
        dataset.setFileName(original);
        dataset.setFilePath("");
        dataset.setFileType(ext);
        dataset.setStatus("UPLOADED");
        dataset.setUploadedBy(user.getId());
        dataset.setVisibility("ADMIN_ONLY");
        dataset.setCreatedAt(now);
        dataset.setUpdatedAt(now);
        datasets.save(dataset);

        Path dir = uploadRoot.resolve("datasets").resolve(String.valueOf(dataset.getId())).normalize();
        ensureInside(uploadRoot, dir);
        Files.createDirectories(dir);
        Path target = dir.resolve(safeFileName(original)).normalize();
        ensureInside(uploadRoot, target);
        file.transferTo(target);
        StoredDatasetFile storedFile = materializeDatasetFile(target, dir, original, ext);
        dataset.setFileName(storedFile.fileName());
        dataset.setFilePath(storedFile.path().toString());
        dataset.setFileType(storedFile.fileType());
        applyValidation(dataset);
        dataset.setUpdatedAt(LocalDateTime.now());
        datasets.save(dataset);
        auditService.log(user.getId(), "UPLOAD_DATASET", "TRAINING_DATASET", dataset.getId().toString(), "{}");
        return toDatasetResponse(dataset);
    }

    @Transactional
    public TrainingDatasetResponse validateDataset(Long id, UserEntity user) {
        TrainingDatasetEntity dataset = requireDataset(id);
        applyValidation(dataset);
        dataset.setUpdatedAt(LocalDateTime.now());
        auditService.log(user.getId(), "VALIDATE_DATASET", "TRAINING_DATASET", dataset.getId().toString(), "{}");
        return toDatasetResponse(dataset);
    }

    @Transactional
    public TrainingDatasetResponse updateDataset(Long id, Map<String, Object> payload) {
        TrainingDatasetEntity dataset = requireDataset(id);
        if (payload.containsKey("name")) dataset.setName(String.valueOf(payload.get("name")));
        if (payload.containsKey("description")) dataset.setDescription(String.valueOf(payload.get("description")));
        if (payload.containsKey("diseaseType")) dataset.setDiseaseType(String.valueOf(payload.get("diseaseType")));
        if (payload.containsKey("sourceName")) dataset.setSourceName(String.valueOf(payload.get("sourceName")));
        if (payload.containsKey("sourceUrl")) dataset.setSourceUrl(String.valueOf(payload.get("sourceUrl")));
        if (payload.containsKey("sourceLicense")) dataset.setSourceLicense(String.valueOf(payload.get("sourceLicense")));
        if (payload.containsKey("visibility")) dataset.setVisibility(VisibilityPolicy.normalize(String.valueOf(payload.get("visibility")), "ADMIN_ONLY"));
        dataset.setUpdatedAt(LocalDateTime.now());
        return toDatasetResponse(dataset);
    }

    @Transactional
    public Map<String, Object> deleteDataset(Long id, UserEntity user) {
        TrainingDatasetEntity dataset = requireDataset(id);
        DeleteDatasetResult result = deleteDatasetRow(dataset);
        auditService.log(user.getId(), "DELETE_DATASET", "TRAINING_DATASET", id.toString(), writeMap(Map.of(
                "deletedTrainingJobs", result.deletedTrainingJobs(),
                "deletedEvaluations", result.deletedEvaluations(),
                "deletedFeedback", result.deletedFeedback())));
        return Map.of(
                "deleted", true,
                "id", id,
                "deletedTrainingJobs", result.deletedTrainingJobs(),
                "deletedEvaluations", result.deletedEvaluations(),
                "deletedFeedback", result.deletedFeedback());
    }

    @Transactional
    public Map<String, Object> pruneSmallDatasets(UserEntity user) {
        List<TrainingDatasetEntity> rows = datasets.findTop200ByOrderByCreatedAtDesc().stream()
                .filter(this::isSmallRemovableDataset)
                .toList();
        int deletedJobs = 0;
        int deletedEvaluations = 0;
        int deletedFeedback = 0;
        List<Long> deletedIds = new ArrayList<>();
        for (TrainingDatasetEntity dataset : rows) {
            DeleteDatasetResult result = deleteDatasetRow(dataset);
            deletedIds.add(dataset.getId());
            deletedJobs += result.deletedTrainingJobs();
            deletedEvaluations += result.deletedEvaluations();
            deletedFeedback += result.deletedFeedback();
        }
        auditService.log(user.getId(), "PRUNE_SMALL_DATASETS", "TRAINING_DATASET", "SMALL", writeMap(Map.of(
                "deletedDatasets", deletedIds.size(),
                "deletedTrainingJobs", deletedJobs,
                "deletedEvaluations", deletedEvaluations,
                "deletedFeedback", deletedFeedback,
                "threshold", SMALL_DATASET_THRESHOLD)));
        return Map.of(
                "deleted", true,
                "deletedDatasets", deletedIds.size(),
                "deletedDatasetIds", deletedIds,
                "deletedTrainingJobs", deletedJobs,
                "deletedEvaluations", deletedEvaluations,
                "deletedFeedback", deletedFeedback,
                "threshold", SMALL_DATASET_THRESHOLD);
    }

    public List<TrainingJobResponse> listJobs() {
        return jobs.findTop200ByOrderByIdDesc().stream().map(this::syncAndMapJob).toList();
    }

    public List<Map<String, Object>> modelCapabilities() {
        try {
            List<Map<String, Object>> response = restTemplate.getForObject(modelServiceUrl + "/models/capabilities", List.class);
            if (response != null && !response.isEmpty()) {
                return response;
            }
        } catch (RestClientException ignored) {
            // Keep the admin UI usable when the model service is temporarily unavailable.
        }
        return List.of(
                capability("xgboost", "XGBoost 稳定基线", true, null),
                capability("logistic_regression", "Logistic Regression 可解释基线", true, null),
                capability("random_forest", "Random Forest 随机森林", true, null),
                capability("extra_trees", "ExtraTrees 极端随机树", true, null),
                capability("hist_gradient_boosting", "HistGradientBoosting 直方图提升树", true, null),
                capability("lightgbm", "LightGBM 梯度提升树", false, "模型服务不可用或未安装可选依赖 lightgbm"),
                capability("catboost", "CatBoost 类别特征提升树", false, "模型服务不可用或未安装可选依赖 catboost"),
                capability("tabpfn", "TabPFN 表格基础模型", false, "模型服务不可用或未安装可选依赖"),
                capability("tabicl", "TabICL 表格上下文学习模型", false, "模型服务不可用或未安装可选依赖"),
                capability("ft_transformer", "FT-Transformer 论文模型", false, "需要额外深度表格训练运行时"));
    }

    @Transactional
    public TrainingJobResponse createJob(TrainingJobRequest request, UserEntity user) {
        TrainingDatasetEntity dataset = requireDataset(request.datasetId());
        if (!"VALID".equals(dataset.getStatus())) {
            throw new IllegalArgumentException("数据集未通过校验，不能训练");
        }
        TrainingDatasetEntity evaluationDataset = null;
        if (request.evaluationDatasetId() != null) {
            evaluationDataset = requireDataset(request.evaluationDatasetId());
            if (!"VALID".equals(evaluationDataset.getStatus())) {
                throw new IllegalArgumentException("评估数据集未通过校验，不能用于训练后评估");
            }
            if (!Objects.equals(dataset.getDiseaseType(), evaluationDataset.getDiseaseType())) {
                throw new IllegalArgumentException("训练数据集和评估数据集必须属于同一病种");
            }
        }
        LocalDateTime now = LocalDateTime.now();
        String modelType = normalizeModelType(request.modelType());
        Map<String, Object> hyperparameters = normalizeHyperparameters(modelType, request);
        ModelTrainingJobEntity job = new ModelTrainingJobEntity();
        job.setDatasetId(dataset.getId());
        job.setEvaluationDatasetId(evaluationDataset == null ? null : evaluationDataset.getId());
        job.setUserId(user.getId());
        job.setDiseaseType(dataset.getDiseaseType());
        job.setModelName(request.modelName());
        job.setModelType(modelType);
        job.setTrainStatus("准备训练");
        job.setProgress(0);
        Object iterationValue = hyperparameters.containsKey("nEstimators") ? hyperparameters.get("nEstimators") : hyperparameters.get("maxIterations");
        job.setTrainEpoch(intValue(iterationValue, request.epochs() == null ? ("xgboost".equals(modelType) ? 80 : 1) : request.epochs()));
        job.setLearningRate(doubleValue(hyperparameters.get("learningRate"), "xgboost".equals(modelType) ? (request.learningRate() == null ? 0.05 : request.learningRate()) : 0.0));
        job.setTestSize(doubleValue(hyperparameters.get("testSize"), request.testSize() == null ? 0.2 : request.testSize()));
        job.setHyperparametersJson(writeMap(hyperparameters));
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        jobs.save(job);
        Path outputDir = trainingRoot.resolve("job-" + job.getId()).normalize();
        ensureInside(trainingRoot, outputDir);
        try {
            Map<String, Object> startRequest = new LinkedHashMap<>();
            startRequest.put("taskId", job.getId().toString());
            startRequest.put("datasetPath", dataset.getFilePath());
            startRequest.put("diseaseType", job.getDiseaseType());
            startRequest.put("modelName", job.getModelName());
            startRequest.put("modelType", job.getModelType());
            startRequest.put("epochs", job.getTrainEpoch());
            startRequest.put("learningRate", job.getLearningRate());
            startRequest.put("testSize", job.getTestSize());
            startRequest.put("hyperparameters", hyperparameters);
            startRequest.put("outputDir", outputDir.toString());
            if (evaluationDataset != null) {
                startRequest.put("evaluationDatasetPath", evaluationDataset.getFilePath());
                startRequest.put("evaluationDatasetName", evaluationDataset.getName());
                startRequest.put("evaluationDatasetSource", evaluationDataset.getSourceName());
                startRequest.put("evaluationDatasetUrl", evaluationDataset.getSourceUrl());
                startRequest.put("evaluationDatasetLicense", evaluationDataset.getSourceLicense());
                startRequest.put("evaluationSampleCount", evaluationDataset.getSampleCount());
            }
            Map<String, Object> response = restTemplate.postForObject(modelServiceUrl + "/training/start", startRequest, Map.class);
            applyTaskResponse(job, response);
        } catch (RestClientException ex) {
            job.setTrainStatus("训练失败");
            job.setMessage("模型服务启动训练失败: " + ex.getMessage());
        }
        job.setUpdatedAt(LocalDateTime.now());
        jobs.save(job);
        auditService.log(user.getId(), "CREATE_TRAINING_JOB", "MODEL_TRAINING_JOB", job.getId().toString(), "{}");
        return toJobResponse(job);
    }

    @Transactional
    public TrainingJobResponse getJobStatus(Long id) {
        return syncAndMapJob(requireJob(id));
    }

    @Transactional
    public TrainingJobResponse stopJob(Long id, UserEntity user) {
        ModelTrainingJobEntity job = requireJob(id);
        try {
            Map<String, Object> response = restTemplate.postForObject(modelServiceUrl + "/training/" + id + "/stop", Map.of(), Map.class);
            applyTaskResponse(job, response);
        } catch (RestClientException ex) {
            job.setTrainStatus("训练终止");
            job.setMessage("已在业务端标记终止，模型服务未确认: " + ex.getMessage());
        }
        job.setUpdatedAt(LocalDateTime.now());
        auditService.log(user.getId(), "STOP_TRAINING_JOB", "MODEL_TRAINING_JOB", job.getId().toString(), "{}");
        return toJobResponse(job);
    }

    public TrainingHistoryResponse getJobHistory(Long id) {
        ModelTrainingJobEntity job = requireJob(id);
        if (job.getHistoryPath() != null && Files.exists(Path.of(job.getHistoryPath()))) {
            return new TrainingHistoryResponse(id, readHistory(Path.of(job.getHistoryPath())));
        }
        try {
            Map<String, Object> response = restTemplate.getForObject(modelServiceUrl + "/training/" + id + "/history", Map.class);
            Object history = response == null ? null : response.get("history");
            return new TrainingHistoryResponse(id, objectMapper.convertValue(history, new TypeReference<>() {}));
        } catch (Exception ex) {
            return new TrainingHistoryResponse(id, Map.of());
        }
    }

    @Transactional
    public void deleteJob(Long id) {
        ModelTrainingJobEntity job = requireJob(id);
        deleteQuietly(trainingRoot.resolve("job-" + id));
        jobs.delete(job);
    }

    @Transactional
    public ModelVersionResponse activateModel(Long modelVersionId, UserEntity user) {
        ModelVersionEntity model = modelVersions.findById(modelVersionId)
                .orElseThrow(() -> new EntityNotFoundException("模型版本不存在"));
        for (ModelVersionEntity item : modelVersions.findByDiseaseTypeOrderByCreatedAtDesc(model.getDiseaseType())) {
            item.setActive(item.getId().equals(model.getId()));
        }
        try {
            restTemplate.postForObject(modelServiceUrl + "/models/activate", Map.of(
                    "diseaseType", model.getDiseaseType(),
                    "version", model.getVersion(),
                    "modelPath", model.getModelPath()), Map.class);
        } catch (RestClientException ex) {
            throw new IllegalStateException("模型服务激活失败: " + ex.getMessage());
        }
        auditService.log(user.getId(), "ACTIVATE_MODEL", "MODEL_VERSION", model.getId().toString(), "{}");
        return toModelVersionResponse(model);
    }

    @Transactional
    public Map<String, Object> deleteModelVersion(Long modelVersionId, UserEntity user) {
        ModelVersionEntity model = modelVersions.findById(modelVersionId)
                .orElseThrow(() -> new EntityNotFoundException("模型版本不存在"));
        List<ModelEvaluationEntity> linkedEvaluations = evaluations.findByModelVersionId(model.getId());
        List<Long> evaluationIds = linkedEvaluations.stream().map(ModelEvaluationEntity::getId).toList();
        Set<ModelFeedbackEntity> linkedFeedback = new LinkedHashSet<>(feedbacks.findByModelVersionId(model.getId()));
        if (!evaluationIds.isEmpty()) {
            linkedFeedback.addAll(feedbacks.findByEvaluationIdIn(evaluationIds));
        }
        List<ModelTrainingJobEntity> linkedJobs = jobs.findByModelVersion(model.getVersion());

        if (!linkedFeedback.isEmpty()) {
            feedbacks.deleteAll(linkedFeedback);
        }
        if (!linkedEvaluations.isEmpty()) {
            evaluations.deleteAll(linkedEvaluations);
        }
        for (ModelTrainingJobEntity job : linkedJobs) {
            deleteQuietly(trainingRoot.resolve("job-" + job.getId()));
        }
        if (!linkedJobs.isEmpty()) {
            jobs.deleteAll(linkedJobs);
        }
        modelVersions.delete(model);
        auditService.log(user.getId(), "DELETE_MODEL_VERSION", "MODEL_VERSION", model.getId().toString(), writeMap(Map.of(
                "version", model.getVersion(),
                "deletedJobs", linkedJobs.size(),
                "deletedEvaluations", linkedEvaluations.size(),
                "deletedFeedback", linkedFeedback.size())));
        return Map.of(
                "deleted", true,
                "id", model.getId(),
                "deletedJobs", linkedJobs.size(),
                "deletedEvaluations", linkedEvaluations.size(),
                "deletedFeedback", linkedFeedback.size());
    }

    public List<ModelEvaluationResponse> listEvaluations() {
        return evaluations.findTop200ByOrderByIdDesc().stream().map(this::toEvaluationResponse).toList();
    }

    @Transactional
    public ModelEvaluationResponse createEvaluation(ModelEvaluationRequest request, UserEntity user) {
        ModelVersionEntity model = modelVersions.findById(request.modelVersionId())
                .orElseThrow(() -> new EntityNotFoundException("模型版本不存在"));
        TrainingDatasetEntity dataset = requireDataset(request.datasetId());
        String modelPath = resolveModelPath(model);
        if (blank(modelPath)) {
            throw new IllegalStateException("模型版本缺少训练产物，请先刷新训练任务状态或重新训练");
        }
        try {
            Map<String, Object> response = restTemplate.postForObject(
                    modelServiceUrl + "/evaluate/" + model.getVersion(),
                    Map.of("datasetPath", dataset.getFilePath(), "modelPath", modelPath),
                    Map.class);
            ModelEvaluationEntity evaluation = new ModelEvaluationEntity();
            evaluation.setModelVersionId(model.getId());
            evaluation.setDatasetId(dataset.getId());
            evaluation.setUserId(user.getId());
            evaluation.setMetricsJson(writeCompactMetrics(response == null ? Map.of() : response.get("metrics")));
            evaluation.setPredictionsJson(objectMapper.writeValueAsString(response == null ? List.of() : response.get("predictions")));
            evaluation.setCreatedAt(LocalDateTime.now());
            evaluations.save(evaluation);
            auditService.log(user.getId(), "CREATE_MODEL_EVALUATION", "MODEL_EVALUATION", evaluation.getId().toString(), "{}");
            return toEvaluationResponse(evaluation);
        } catch (Exception ex) {
            throw new IllegalStateException("模型评估失败: " + ex.getMessage());
        }
    }

    public List<ModelFeedbackResponse> listFeedback() {
        return feedbacks.findTop200ByOrderByIdDesc().stream().map(this::toFeedbackResponse).toList();
    }

    @Transactional
    public ModelFeedbackResponse createFeedback(ModelFeedbackRequest request, UserEntity user) {
        ModelFeedbackEntity feedback = new ModelFeedbackEntity();
        applyFeedback(feedback, request);
        feedback.setUserId(user.getId());
        feedback.setMetricsSnapshotJson(metricsSnapshot(request.evaluationId(), request.modelVersionId()));
        feedback.setCreatedAt(LocalDateTime.now());
        feedback.setUpdatedAt(LocalDateTime.now());
        feedbacks.save(feedback);
        auditService.log(user.getId(), "CREATE_MODEL_FEEDBACK", "MODEL_FEEDBACK", feedback.getId().toString(), "{}");
        return toFeedbackResponse(feedback);
    }

    @Transactional
    public ModelFeedbackResponse updateFeedback(Long id, ModelFeedbackRequest request) {
        ModelFeedbackEntity feedback = feedbacks.findById(id).orElseThrow(() -> new EntityNotFoundException("反馈不存在"));
        applyFeedback(feedback, request);
        feedback.setMetricsSnapshotJson(metricsSnapshot(request.evaluationId(), request.modelVersionId()));
        feedback.setUpdatedAt(LocalDateTime.now());
        return toFeedbackResponse(feedback);
    }

    @Transactional
    public void deleteFeedback(Long id) {
        feedbacks.deleteById(id);
    }

    private TrainingJobResponse syncAndMapJob(ModelTrainingJobEntity job) {
        if (!TERMINAL_STATUS.contains(job.getTrainStatus())) {
            try {
                Map<String, Object> response = restTemplate.getForObject(modelServiceUrl + "/training/" + job.getId() + "/status", Map.class);
                applyTaskResponse(job, response);
                job.setUpdatedAt(LocalDateTime.now());
                jobs.save(job);
            } catch (RestClientException ignored) {
                // Keep persisted status if the model service is unavailable.
            }
        }
        if ("训练成功".equals(job.getTrainStatus()) && job.getModelVersion() != null) {
            promoteModelVersion(job);
        }
        return toJobResponse(job);
    }

    private void applyTaskResponse(ModelTrainingJobEntity job, Map<String, Object> response) {
        if (response == null) return;
        job.setTrainStatus(string(response.get("status"), job.getTrainStatus()));
        job.setProgress(intValue(response.get("progress"), job.getProgress()));
        job.setCurrentLoss(doubleValue(response.get("currentLoss")));
        job.setMessage(string(response.get("message"), job.getMessage()));
        job.setModelVersion(string(response.get("modelVersion"), job.getModelVersion()));
        job.setModelPath(string(response.get("modelPath"), job.getModelPath()));
        job.setHistoryPath(string(response.get("historyPath"), job.getHistoryPath()));
        job.setMetadataPath(string(response.get("metadataPath"), job.getMetadataPath()));
        Object hyperparameters = response.get("hyperparameters");
        if (hyperparameters != null) {
            try {
                job.setHyperparametersJson(objectMapper.writeValueAsString(hyperparameters));
            } catch (Exception ignored) {
                // Keep existing hyperparameter record.
            }
        }
        Object metrics = response.get("metrics");
        if (metrics != null) {
            try {
                job.setMetricsJson(writeCompactMetrics(metrics));
            } catch (Exception ignored) {
                job.setMetricsJson("{}");
            }
        }
        if ("训练成功".equals(job.getTrainStatus()) && job.getModelVersion() != null) {
            promoteModelVersion(job);
        }
    }

    private ModelVersionEntity promoteModelVersion(ModelTrainingJobEntity job) {
        ModelVersionEntity model = modelVersions.findByVersion(job.getModelVersion()).orElseGet(() -> {
            ModelVersionEntity version = new ModelVersionEntity();
            version.setDiseaseType(job.getDiseaseType());
            version.setDiseaseName(diseaseName(job.getDiseaseType()));
            version.setModelName(job.getModelName());
            version.setModelType(normalizeModelType(job.getModelType()));
            version.setVersion(job.getModelVersion());
            version.setMetricsJson(job.getMetricsJson() == null ? "{}" : writeCompactMetrics(readMap(job.getMetricsJson())));
            version.setFeatureSchemaJson("[]");
            version.setHyperparametersJson(job.getHyperparametersJson());
            TrainingDatasetEntity evaluationDataset = job.getEvaluationDatasetId() == null ? null : datasets.findById(job.getEvaluationDatasetId()).orElse(null);
            if (evaluationDataset != null) {
                version.setEvaluationDatasetName(evaluationDataset.getName());
                version.setEvaluationDatasetSource(evaluationDataset.getSourceName());
                version.setEvaluationDatasetUrl(evaluationDataset.getSourceUrl());
            } else {
                Map<String, Object> metrics = readMetricsMap(job.getMetricsJson());
                version.setEvaluationDatasetName(string(metrics.get("evaluationDataset"), null));
                version.setEvaluationDatasetSource(string(metrics.get("datasetSource"), null));
                version.setEvaluationDatasetUrl(string(metrics.get("datasetUrl"), null));
            }
            version.setModelPath(job.getModelPath());
            version.setActive(false);
            version.setCreatedAt(LocalDateTime.now());
            return modelVersions.save(version);
        });
        boolean changed = false;
        if (blank(model.getModelPath()) && !blank(job.getModelPath())) {
            model.setModelPath(job.getModelPath());
            changed = true;
        }
        if (blank(model.getMetricsJson()) && !blank(job.getMetricsJson())) {
            model.setMetricsJson(writeCompactMetrics(readMap(job.getMetricsJson())));
            changed = true;
        }
        if (blank(model.getHyperparametersJson()) && !blank(job.getHyperparametersJson())) {
            model.setHyperparametersJson(job.getHyperparametersJson());
            changed = true;
        }
        if (changed) {
            model = modelVersions.save(model);
        }
        activatePromotedModel(model);
        return model;
    }

    private String resolveModelPath(ModelVersionEntity model) {
        if (!blank(model.getModelPath())) {
            return model.getModelPath();
        }
        for (ModelTrainingJobEntity job : jobs.findByModelVersion(model.getVersion())) {
            if (!blank(job.getModelPath())) {
                model.setModelPath(job.getModelPath());
                modelVersions.save(model);
                return job.getModelPath();
            }
            Path fallback = trainingRoot.resolve("job-" + job.getId()).resolve("model.joblib").normalize();
            if (Files.exists(fallback)) {
                String modelPath = fallback.toString();
                job.setModelPath(modelPath);
                model.setModelPath(modelPath);
                jobs.save(job);
                modelVersions.save(model);
                return modelPath;
            }
        }
        return null;
    }

    private void activatePromotedModel(ModelVersionEntity model) {
        if (Boolean.TRUE.equals(model.getActive()) || model.getModelPath() == null || model.getModelPath().isBlank()) {
            return;
        }
        try {
            restTemplate.postForObject(modelServiceUrl + "/models/activate", Map.of(
                    "diseaseType", model.getDiseaseType(),
                    "version", model.getVersion(),
                    "modelPath", model.getModelPath()), Map.class);
        } catch (RestClientException ignored) {
            return;
        }
        List<ModelVersionEntity> diseaseModels = modelVersions.findByDiseaseTypeOrderByCreatedAtDesc(model.getDiseaseType());
        for (ModelVersionEntity item : diseaseModels) {
            item.setActive(item.getId().equals(model.getId()));
        }
        modelVersions.saveAll(diseaseModels);
    }

    private void applyValidation(TrainingDatasetEntity dataset) {
        try {
            DatasetValidation validation = validateDatasetFile(Path.of(dataset.getFilePath()));
            dataset.setSampleCount(validation.sampleCount());
            dataset.setFeatureColumns(objectMapper.writeValueAsString(validation.featureColumns()));
            dataset.setStatus("VALID");
            dataset.setValidationMessage("校验通过");
        } catch (Exception ex) {
            dataset.setStatus("INVALID");
            dataset.setValidationMessage(ex.getMessage());
        }
    }

    private DatasetValidation validateDatasetFile(Path path) throws IOException {
        try (BufferedReader reader = openDatasetReader(path)) {
            String header = nextNonBlankLine(reader);
            if (header == null) {
                throw new IllegalArgumentException("数据集为空");
            }
            List<String> columns = parseCsvLine(stripBom(header)).stream().map(String::trim).toList();
            if (!columns.contains("label")) {
                throw new IllegalArgumentException("数据集必须包含 label 列");
            }
            List<String> features = columns.stream().filter(item -> !"label".equals(item)).toList();
            if (features.isEmpty()) {
                throw new IllegalArgumentException("数据集至少需要一个特征列");
            }
            int count = 0;
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) count++;
            }
            if (count < 8) {
                throw new IllegalArgumentException("训练数据至少需要 8 条样本");
            }
            return new DatasetValidation(count, features);
        }
    }

    private BufferedReader openDatasetReader(Path path) throws IOException {
        if (path.toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            ZipInputStream zip = new ZipInputStream(Files.newInputStream(path), StandardCharsets.UTF_8);
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().toLowerCase(Locale.ROOT).endsWith(".csv")) {
                    return new BufferedReader(new InputStreamReader(zip, StandardCharsets.UTF_8));
                }
            }
            zip.close();
            throw new IllegalArgumentException("zip 数据集中没有 CSV 文件");
        }
        return Files.newBufferedReader(path, StandardCharsets.UTF_8);
    }

    private StoredDatasetFile materializeDatasetFile(Path storedPath, Path dir, String original, String ext) throws IOException {
        if (!"zip".equals(ext)) {
            return new StoredDatasetFile(original, storedPath, ext);
        }
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(storedPath), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory() || !entry.getName().toLowerCase(Locale.ROOT).endsWith(".csv")) {
                    continue;
                }
                String csvName = fileNameOnly(entry.getName(), "dataset.csv");
                Path extracted = dir.resolve("extracted-" + safeFileName(csvName)).normalize();
                ensureInside(uploadRoot, extracted);
                Files.copy(zip, extracted, StandardCopyOption.REPLACE_EXISTING);
                return new StoredDatasetFile(csvName, extracted, "csv");
            }
        } catch (IOException ignored) {
            return new StoredDatasetFile(original, storedPath, ext);
        }
        return new StoredDatasetFile(original, storedPath, ext);
    }

    private String nextNonBlankLine(BufferedReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.isBlank()) {
                return line;
            }
        }
        return null;
    }

    private String stripBom(String value) {
        return value != null && !value.isEmpty() && value.charAt(0) == '\uFEFF' ? value.substring(1) : value;
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        values.add(current.toString());
        return values;
    }

    private void applyFeedback(ModelFeedbackEntity feedback, ModelFeedbackRequest request) {
        feedback.setModelVersionId(request.modelVersionId());
        feedback.setEvaluationId(request.evaluationId());
        feedback.setProblemType(request.problemType());
        feedback.setPriority(request.priority());
        feedback.setStatus(request.status());
        feedback.setContent(request.content());
    }

    private TrainingDatasetEntity requireDataset(Long id) {
        return datasets.findById(id).orElseThrow(() -> new EntityNotFoundException("数据集不存在"));
    }

    private ModelTrainingJobEntity requireJob(Long id) {
        return jobs.findById(id).orElseThrow(() -> new EntityNotFoundException("训练任务不存在"));
    }

    private TrainingDatasetResponse toDatasetResponse(TrainingDatasetEntity dataset) {
        return new TrainingDatasetResponse(
                dataset.getId(),
                dataset.getName(),
                dataset.getDiseaseType(),
                dataset.getDescription(),
                dataset.getFileName(),
                dataset.getFilePath(),
                dataset.getFileType(),
                dataset.getStatus(),
                dataset.getSampleCount(),
                readList(dataset.getFeatureColumns()),
                dataset.getValidationMessage(),
                dataset.getUploadedBy(),
                dataset.getSourceName(),
                dataset.getSourceUrl(),
                dataset.getSourceLicense(),
                dataset.getSourceRecordId(),
                dataset.getVisibility(),
                dataset.getCreatedAt(),
                dataset.getUpdatedAt());
    }

    private TrainingJobResponse toJobResponse(ModelTrainingJobEntity job) {
        TrainingDatasetEntity dataset = datasets.findById(job.getDatasetId()).orElse(null);
        TrainingDatasetEntity evaluationDataset = job.getEvaluationDatasetId() == null ? null : datasets.findById(job.getEvaluationDatasetId()).orElse(null);
        return new TrainingJobResponse(
                job.getId(),
                job.getDatasetId(),
                dataset == null ? "" : dataset.getName(),
                job.getEvaluationDatasetId(),
                evaluationDataset == null ? null : evaluationDataset.getName(),
                evaluationDataset == null ? null : evaluationDataset.getSourceName(),
                evaluationDataset == null ? null : evaluationDataset.getSourceUrl(),
                job.getUserId(),
                job.getDiseaseType(),
                job.getModelName(),
                normalizeModelType(job.getModelType()),
                job.getTrainStatus(),
                job.getProgress(),
                job.getCurrentLoss(),
                job.getTrainEpoch(),
                job.getLearningRate(),
                job.getTestSize(),
                readMap(job.getHyperparametersJson()),
                job.getModelVersion(),
                job.getModelPath(),
                job.getHistoryPath(),
                job.getMetadataPath(),
                readMetricsMap(job.getMetricsJson()),
                job.getMessage(),
                job.getCreatedAt(),
                job.getUpdatedAt());
    }

    private ModelEvaluationResponse toEvaluationResponse(ModelEvaluationEntity evaluation) {
        ModelVersionEntity model = modelVersions.findById(evaluation.getModelVersionId()).orElse(null);
        TrainingDatasetEntity dataset = datasets.findById(evaluation.getDatasetId()).orElse(null);
        return new ModelEvaluationResponse(
                evaluation.getId(),
                evaluation.getModelVersionId(),
                model == null ? "" : model.getVersion(),
                evaluation.getDatasetId(),
                dataset == null ? "" : dataset.getName(),
                readMetricsMap(evaluation.getMetricsJson()),
                readListOfMaps(evaluation.getPredictionsJson()),
                evaluation.getCreatedAt());
    }

    private ModelFeedbackResponse toFeedbackResponse(ModelFeedbackEntity feedback) {
        ModelVersionEntity model = feedback.getModelVersionId() == null ? null : modelVersions.findById(feedback.getModelVersionId()).orElse(null);
        return new ModelFeedbackResponse(
                feedback.getId(),
                feedback.getModelVersionId(),
                model == null ? "" : model.getVersion(),
                feedback.getEvaluationId(),
                feedback.getUserId(),
                feedback.getProblemType(),
                feedback.getPriority(),
                feedback.getStatus(),
                feedback.getContent(),
                readMetricsMap(feedback.getMetricsSnapshotJson()),
                feedback.getCreatedAt(),
                feedback.getUpdatedAt());
    }

    private ModelVersionResponse toModelVersionResponse(ModelVersionEntity model) {
        Map<String, Object> metrics = readMetricsMap(model.getMetricsJson());
        return new ModelVersionResponse(
                model.getId(),
                model.getDiseaseType(),
                model.getDiseaseName(),
                model.getModelName(),
                normalizeModelType(model.getModelType()),
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
                return writeMap(readMetricsMap(text));
            }
            Map<String, Object> metrics = objectMapper.convertValue(payload, new TypeReference<>() {});
            return writeMap(compactMetrics(metrics));
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

    private String writeMap(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private Map<String, Object> normalizeHyperparameters(String modelType, TrainingJobRequest request) {
        Map<String, Object> input = request.hyperparameters() == null ? Map.of() : request.hyperparameters();
        Map<String, Object> output = new LinkedHashMap<>();
        if ("logistic_regression".equals(modelType)) {
            output.put("cValue", doubleValue(input.get("cValue"), 1.0));
            output.put("maxIterations", intValue(input.get("maxIterations"), 300));
            output.put("classWeight", string(input.get("classWeight"), "balanced"));
            output.put("testSize", doubleValue(input.get("testSize"), request.testSize() == null ? 0.2 : request.testSize()));
            return output;
        }
        if ("random_forest".equals(modelType) || "extra_trees".equals(modelType)) {
            output.put("nEstimators", intValue(input.get("nEstimators"), request.epochs() == null ? 160 : request.epochs()));
            output.put("maxDepth", intValue(input.get("maxDepth"), 6));
            output.put("classWeight", string(input.get("classWeight"), "balanced"));
            output.put("seed", intValue(input.get("seed"), 42));
            output.put("testSize", doubleValue(input.get("testSize"), request.testSize() == null ? 0.2 : request.testSize()));
            return output;
        }
        if ("hist_gradient_boosting".equals(modelType)) {
            output.put("maxIterations", intValue(input.get("maxIterations"), request.epochs() == null ? 120 : request.epochs()));
            output.put("maxDepth", intValue(input.get("maxDepth"), 4));
            output.put("learningRate", doubleValue(input.get("learningRate"), request.learningRate() == null ? 0.05 : request.learningRate()));
            output.put("regLambda", doubleValue(input.get("regLambda"), 0.0));
            output.put("seed", intValue(input.get("seed"), 42));
            output.put("testSize", doubleValue(input.get("testSize"), request.testSize() == null ? 0.2 : request.testSize()));
            return output;
        }
        if ("lightgbm".equals(modelType) || "catboost".equals(modelType)) {
            output.put("nEstimators", intValue(input.get("nEstimators"), request.epochs() == null ? 160 : request.epochs()));
            output.put("maxDepth", intValue(input.get("maxDepth"), 6));
            output.put("learningRate", doubleValue(input.get("learningRate"), request.learningRate() == null ? 0.05 : request.learningRate()));
            output.put("subsample", doubleValue(input.get("subsample"), 0.9));
            output.put("colsampleBytree", doubleValue(input.get("colsampleBytree"), 0.9));
            output.put("regLambda", doubleValue(input.get("regLambda"), "catboost".equals(modelType) ? 3.0 : 1.0));
            output.put("classWeight", string(input.get("classWeight"), "balanced"));
            output.put("seed", intValue(input.get("seed"), 42));
            output.put("testSize", doubleValue(input.get("testSize"), request.testSize() == null ? 0.2 : request.testSize()));
            return output;
        }
        if ("tabpfn".equals(modelType)) {
            output.put("maxTrainSamples", intValue(input.get("maxTrainSamples"), 2048));
            output.put("device", string(input.get("device"), "auto"));
            output.put("ensembleSize", intValue(input.get("ensembleSize"), 8));
            output.put("version", string(input.get("version"), "v2"));
            output.put("testSize", doubleValue(input.get("testSize"), request.testSize() == null ? 0.2 : request.testSize()));
            return output;
        }
        if ("tabicl".equals(modelType)) {
            output.put("contextSize", intValue(input.get("contextSize"), 2048));
            output.put("maxTrainSamples", intValue(input.get("maxTrainSamples"), 10000));
            output.put("device", string(input.get("device"), "auto"));
            output.put("seed", intValue(input.get("seed"), 42));
            output.put("testSize", doubleValue(input.get("testSize"), request.testSize() == null ? 0.2 : request.testSize()));
            return output;
        }
        output.put("nEstimators", intValue(input.get("nEstimators"), request.epochs() == null ? 80 : request.epochs()));
        output.put("maxDepth", intValue(input.get("maxDepth"), 3));
        output.put("learningRate", doubleValue(input.get("learningRate"), request.learningRate() == null ? 0.05 : request.learningRate()));
        output.put("subsample", doubleValue(input.get("subsample"), 0.9));
        output.put("colsampleBytree", doubleValue(input.get("colsampleBytree"), 0.9));
        output.put("regLambda", doubleValue(input.get("regLambda"), 1.0));
        output.put("minChildWeight", doubleValue(input.get("minChildWeight"), 1.0));
        output.put("testSize", doubleValue(input.get("testSize"), request.testSize() == null ? 0.2 : request.testSize()));
        return output;
    }

    private List<String> readList(String json) {
        try {
            return json == null || json.isBlank() ? List.of() : objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<Map<String, Object>> readListOfMaps(String json) {
        try {
            return json == null || json.isBlank() ? List.of() : objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private Map<String, List<Double>> readHistory(Path path) {
        try {
            return objectMapper.readValue(path.toFile(), new TypeReference<>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String metricsSnapshot(Long evaluationId, Long modelVersionId) {
        if (evaluationId != null) {
            return evaluations.findById(evaluationId)
                    .map(row -> writeMap(readMetricsMap(row.getMetricsJson())))
                    .orElse("{}");
        }
        if (modelVersionId != null) {
            return modelVersions.findById(modelVersionId)
                    .map(row -> writeMap(readMetricsMap(row.getMetricsJson())))
                    .orElse("{}");
        }
        return "{}";
    }

    private String extension(String filename) {
        int index = filename.lastIndexOf('.');
        return index < 0 ? "" : filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String safeFileName(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9._\\-\\u4e00-\\u9fa5]", "_");
    }

    private void ensureInside(Path root, Path target) {
        if (!target.toAbsolutePath().normalize().startsWith(root.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("文件路径越界");
        }
    }

    private void deleteQuietly(Path path) {
        if (path == null || !Files.exists(path)) return;
        try (var walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(item -> {
                try {
                    Files.deleteIfExists(item);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private String string(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private Integer intValue(Object value, Integer fallback) {
        if (value instanceof Number number) return number.intValue();
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (Exception ex) {
            return fallback;
        }
    }

    private Double doubleValue(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        try {
            return value == null ? null : Double.parseDouble(String.valueOf(value));
        } catch (Exception ex) {
            return null;
        }
    }

    private Double doubleValue(Object value, Double fallback) {
        Double parsed = doubleValue(value);
        return parsed == null ? fallback : parsed;
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private DeleteDatasetResult deleteDatasetRow(TrainingDatasetEntity dataset) {
        List<ModelEvaluationEntity> datasetEvaluations = evaluations.findByDatasetId(dataset.getId());
        List<Long> evaluationIds = datasetEvaluations.stream().map(ModelEvaluationEntity::getId).toList();
        List<ModelFeedbackEntity> linkedFeedback = evaluationIds.isEmpty() ? List.of() : feedbacks.findByEvaluationIdIn(evaluationIds);
        if (!linkedFeedback.isEmpty()) {
            feedbacks.deleteAll(linkedFeedback);
        }
        if (!datasetEvaluations.isEmpty()) {
            evaluations.deleteAll(datasetEvaluations);
        }
        List<ModelTrainingJobEntity> linkedJobs = jobs.findByDatasetIdOrEvaluationDatasetId(dataset.getId(), dataset.getId());
        if (!linkedJobs.isEmpty()) {
            jobs.deleteAll(linkedJobs);
        }
        deleteDatasetFiles(dataset);
        datasets.delete(dataset);
        return new DeleteDatasetResult(linkedJobs.size(), datasetEvaluations.size(), linkedFeedback.size());
    }

    private boolean isSmallRemovableDataset(TrainingDatasetEntity dataset) {
        int sampleCount = dataset.getSampleCount() == null ? 0 : dataset.getSampleCount();
        if (sampleCount >= SMALL_DATASET_THRESHOLD) return false;
        String sourceName = dataset.getSourceName() == null ? "" : dataset.getSourceName();
        String sourceRecordId = dataset.getSourceRecordId() == null ? "" : dataset.getSourceRecordId();
        return sourceRecordId.startsWith("medrisk-demo:dataset:")
                || sourceName.contains("UCI Machine Learning Repository")
                || sourceName.contains("MedRisk Demo Seed Pack");
    }

    private boolean isSmallPublicManifestDataset(Map<String, Object> info) {
        int rows = intValue(info.get("rows"), intValue(info.get("rawRows"), 0));
        if (rows <= 0 || rows >= SMALL_DATASET_THRESHOLD) {
            return false;
        }
        String datasetId = string(info.get("datasetId"), "");
        String sourceName = string(info.get("sourceName"), string(info.get("source"), ""));
        return datasetId.endsWith("_uci") || sourceName.contains("UCI Machine Learning Repository");
    }

    private void deleteDatasetFiles(TrainingDatasetEntity dataset) {
        String filePath = dataset.getFilePath();
        if (filePath == null || filePath.isBlank()) return;
        Path path = Path.of(filePath).toAbsolutePath().normalize();
        Path uploadDatasetRoot = uploadRoot.resolve("datasets").toAbsolutePath().normalize();
        Path publicRoot = publicDatasetRoot.toAbsolutePath().normalize();
        if (path.startsWith(publicRoot)) {
            return;
        }
        if (!path.startsWith(uploadDatasetRoot)) {
            return;
        }
        Path parent = path.getParent();
        if (parent != null && parent.startsWith(uploadDatasetRoot)) {
            deleteQuietly(parent);
        } else {
            deleteQuietly(path);
        }
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

    private String normalizeModelType(String value) {
        String normalized = value == null || value.isBlank() ? "xgboost" : value.trim().toLowerCase(Locale.ROOT);
        return MODEL_TYPES.contains(normalized) ? normalized : "xgboost";
    }

    private Map<String, Object> capability(String modelType, String label, boolean available, String reason) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("modelType", modelType);
        map.put("label", label);
        map.put("available", available);
        if (reason != null) {
            map.put("reason", reason);
        }
        return map;
    }

    private record DatasetValidation(int sampleCount, List<String> featureColumns) {
    }

    private record StoredDatasetFile(String fileName, Path path, String fileType) {
    }

    private record DeleteDatasetResult(int deletedTrainingJobs, int deletedEvaluations, int deletedFeedback) {
    }
}
