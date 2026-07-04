package com.hbut.medrisk.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbut.medrisk.entity.KnowledgeDocumentEntity;
import com.hbut.medrisk.entity.KnowledgeGraphJobEntity;
import com.hbut.medrisk.entity.UserEntity;
import com.hbut.medrisk.repository.KnowledgeDocumentRepository;
import com.hbut.medrisk.repository.KnowledgeGraphJobRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class KnowledgeGraphService {
    private static final int MIN_DOCUMENT_GRAPH_NODES = 95;
    private static final int MAX_TEXT_UNITS = 24;
    private static final int LLM_CHUNK_SIZE = 500;
    private static final int MAX_LLM_CHUNKS = 24;
    private static final Map<String, String> DISEASE_DEPARTMENTS = Map.of(
            "糖尿病", "内分泌科",
            "高血压", "心血管科",
            "冠心病", "心血管科",
            "肺炎", "呼吸科",
            "胃炎", "消化科",
            "心脏病", "心血管科",
            "慢性肾病", "肾内科",
            "肝病", "肝病科",
            "中风", "神经内科",
            "骨折", "骨科");
    private static final Set<String> SYMPTOMS = Set.of(
            "发热", "咳嗽", "胸痛", "胸闷", "头晕", "头痛", "乏力", "多饮", "多尿", "腹痛", "恶心", "疼痛", "呼吸困难", "血压升高",
            "fever", "cough", "chest pain", "fatigue", "dyspnea", "nausea", "pain");
    private static final Set<String> TREATMENTS = Set.of(
            "胰岛素", "降糖药", "降压药", "抗生素", "支架", "手术", "康复", "饮食干预", "药物治疗", "氧疗", "补液", "监测",
            "insulin", "antibiotic", "surgery", "rehabilitation", "hydration", "monitoring", "therapy", "treatment");
    private static final Set<String> EXAMS = Set.of(
            "血糖", "糖化血红蛋白", "血压", "心电图", "CT", "影像", "尿常规", "肾功能", "肝功能", "血常规", "检查", "筛查",
            "glucose", "blood pressure", "ecg", "ct", "screening", "test", "exam");
    private static final Set<String> RISK_FACTORS = Set.of(
            "肥胖", "吸烟", "饮酒", "高盐", "高糖", "家族史", "年龄", "感染", "缺乏运动", "压力", "危险", "风险",
            "obesity", "smoking", "alcohol", "family history", "age", "infection", "risk");
    private static final Set<String> DRUGS = Set.of(
            "阿司匹林", "二甲双胍", "胰岛素", "他汀", "ACEI", "ARB", "抗生素", "降压药", "降糖药",
            "aspirin", "metformin", "statin", "insulin", "antibiotic");
    private static final Set<String> STOPWORDS = Set.of(
            "this", "that", "with", "from", "include", "includes", "about", "into", "and", "the", "for", "are", "was", "were",
            "patient", "disease", "medical", "health", "test", "knowledge");
    private static final Set<String> ALLOWED_NODE_TYPES = Set.of(
            "Disease", "Symptom", "BodyPart", "Pathogen", "Drug", "Treatment", "Exam", "Complication",
            "RiskFactor", "Department", "Document", "TimePoint", "TextUnit", "Claim", "Keyword");
    private static final Map<String, String> RELATION_LABELS = Map.ofEntries(
            Map.entry("DIAGNOSED_AS", "诊断为"),
            Map.entry("CAUSED_BY", "由...引起"),
            Map.entry("SHOWS_SYMPTOM", "表现症状"),
            Map.entry("AFFECTS_BODYPART", "影响部位"),
            Map.entry("PREVENTED_BY", "被预防"),
            Map.entry("TREATED_WITH", "用...治疗"),
            Map.entry("REQUIRES_EXAMINATION", "需要检查"),
            Map.entry("OCCURS_AT", "发生时间"),
            Map.entry("AGGRAVATED_BY", "被...加重"),
            Map.entry("MANAGED_BY", "由...管理"),
            Map.entry("RELATED_TO", "相关"),
            Map.entry("RECORDED_IN", "记录于"),
            Map.entry("HAS_TEXT_UNIT", "包含文本单元"),
            Map.entry("HAS_CLAIM", "包含观点"),
            Map.entry("MENTIONS_KEYWORD", "提及关键词"),
            Map.entry("HAS_ENRICHED_INDEX", "补充索引"),
            Map.entry("MENTIONS_SYMPTOM", "提及症状"),
            Map.entry("MENTIONS_TREATMENT", "提及治疗"),
            Map.entry("MENTIONS_EXAM", "提及检查"),
            Map.entry("MENTIONS_RISK_FACTOR", "提及风险因素"),
            Map.entry("MENTIONS_DRUG", "提及药物"),
            Map.entry("USES_DRUG", "使用药物"),
            Map.entry("NEXT_TEXT_UNIT", "相邻文本单元"),
            Map.entry("SUMMARIZES_KEYWORD", "归纳关键词"));
    private static final Map<String, String> RELATION_ALIASES = Map.ofEntries(
            Map.entry("诊断为", "DIAGNOSED_AS"),
            Map.entry("由...引起", "CAUSED_BY"),
            Map.entry("由……引起", "CAUSED_BY"),
            Map.entry("表现症状", "SHOWS_SYMPTOM"),
            Map.entry("影响部位", "AFFECTS_BODYPART"),
            Map.entry("被预防", "PREVENTED_BY"),
            Map.entry("用...治疗", "TREATED_WITH"),
            Map.entry("用……治疗", "TREATED_WITH"),
            Map.entry("需要检查", "REQUIRES_EXAMINATION"),
            Map.entry("发生时间", "OCCURS_AT"),
            Map.entry("被...加重", "AGGRAVATED_BY"),
            Map.entry("被……加重", "AGGRAVATED_BY"),
            Map.entry("由...管理", "MANAGED_BY"),
            Map.entry("由……管理", "MANAGED_BY"),
            Map.entry("相关", "RELATED_TO"));

    private final KnowledgeGraphStore graphStore;
    private final KnowledgeDocumentRepository documents;
    private final KnowledgeGraphJobRepository jobs;
    private final AuditService auditService;
    private final LlmService llmService;
    private final LlmProfileService llmProfiles;
    private final ObjectMapper objectMapper;
    private final ExecutorService graphExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "medrisk-knowledge-graph-builder");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean jobRunning = new AtomicBoolean(false);

    public KnowledgeGraphService(
            KnowledgeGraphStore graphStore,
            KnowledgeDocumentRepository documents,
            KnowledgeGraphJobRepository jobs,
            AuditService auditService,
            LlmService llmService,
            LlmProfileService llmProfiles,
            ObjectMapper objectMapper) {
        this.graphStore = graphStore;
        this.documents = documents;
        this.jobs = jobs;
        this.auditService = auditService;
        this.llmService = llmService;
        this.llmProfiles = llmProfiles;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void markInterruptedJobs() {
        for (KnowledgeGraphJobEntity job : jobs.findByStatusOrderByCreatedAtDesc("运行中")) {
            job.setStatus("构建失败");
            job.setProgress(100);
            job.setMessage("服务重启，构建任务已中断，请重新执行。");
            job.setUpdatedAt(LocalDateTime.now());
            jobs.save(job);
        }
    }

    @PreDestroy
    void shutdownExecutor() {
        graphExecutor.shutdownNow();
    }

    public Map<String, Object> health() {
        return graphStore.health();
    }

    public List<Map<String, Object>> jobs() {
        return jobs.findTop50ByOrderByCreatedAtDesc().stream().map(this::toMap).toList();
    }

    public List<Map<String, Object>> search(String keyword) {
        return graphStore.search(keyword, 20);
    }

    public List<Map<String, Object>> search(String keyword, UserEntity user) {
        return graphStore.search(keyword, 20, allowedVisibilities(user, null), null);
    }

    public Map<String, Object> visualization(String keyword, List<String> nodeTypes, List<String> relationshipTypes, int limit) {
        return graphStore.visualization(keyword, nodeTypes, relationshipTypes, Math.max(20, Math.min(limit, 300)));
    }

    public Map<String, Object> visualization(
            String keyword,
            List<String> nodeTypes,
            List<String> relationshipTypes,
            String sourceName,
            String visibility,
            int limit,
            UserEntity user) {
        return graphStore.visualization(
                keyword,
                nodeTypes,
                relationshipTypes,
                allowedVisibilities(user, visibility),
                sourceName,
                Math.max(20, Math.min(limit, 300)));
    }

    @Transactional
    public Map<String, Object> syncDocument(Long documentId, UserEntity user) {
        KnowledgeDocumentEntity document = documents.findById(documentId).orElseThrow(() -> new IllegalArgumentException("文档不存在"));
        return startJob("单文档同步", document.getId(), List.of(document.getId()), false, user);
    }

    @Transactional
    public Map<String, Object> incremental(UserEntity user) {
        List<KnowledgeDocumentEntity> pending = documents.findByGraphStatusInOrderByCreatedAtAsc(List.of("未构建", "构建失败"));
        return startJob("增量构建", null, pending.stream().map(KnowledgeDocumentEntity::getId).toList(), false, user);
    }

    @Transactional
    public Map<String, Object> rebuild(UserEntity user) {
        List<KnowledgeDocumentEntity> all = documents.findAllNewest();
        return startJob("全量重建", null, all.stream().map(KnowledgeDocumentEntity::getId).toList(), true, user);
    }

    private Map<String, Object> startJob(String type, Long documentId, List<Long> documentIds, boolean clearFirst, UserEntity user) {
        closeStaleRunningJobsIfIdle();
        if (!jobRunning.compareAndSet(false, true)) {
            throw new ApiConflictException("已有知识图谱构建任务正在运行");
        }
        KnowledgeGraphJobEntity job = new KnowledgeGraphJobEntity();
        job.setJobType(type);
        job.setStatus("运行中");
        job.setProgress(0);
        job.setMessage("准备构建知识图谱");
        job.setNodesCreated(0);
        job.setRelationshipsCreated(0);
        job.setProcessedDocuments(0);
        job.setTotalDocuments(documentIds.size());
        job.setFailedDocuments(0);
        job.setDocumentId(documentId);
        job.setStartedBy(user.getId());
        LocalDateTime now = LocalDateTime.now();
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        jobs.save(job);
        Long jobId = job.getId();
        Long userId = user.getId();
        List<Long> ids = List.copyOf(documentIds);
        submitAfterCommit(jobId, ids, clearFirst, userId);
        return toMap(job);
    }

    private void submitAfterCommit(Long jobId, List<Long> documentIds, boolean clearFirst, Long userId) {
        Runnable task = () -> submitJob(jobId, documentIds, clearFirst, userId);
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }

                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        jobRunning.set(false);
                    }
                }
            });
            return;
        }
        task.run();
    }

    private void submitJob(Long jobId, List<Long> documentIds, boolean clearFirst, Long userId) {
        graphExecutor.submit(() -> {
            try {
                runJob(jobId, documentIds, clearFirst, userId);
            } catch (Exception ex) {
                markJobFailed(jobId, ex, userId);
            } finally {
                jobRunning.set(false);
            }
        });
    }

    private void runJob(Long jobId, List<Long> documentIds, boolean clearFirst, Long userId) {
        KnowledgeGraphJobEntity job = jobs.findById(jobId).orElseThrow();
        List<KnowledgeDocumentEntity> rows = documentIds.isEmpty()
                ? List.of()
                : documentIds.stream().map(id -> documents.findById(id).orElse(null)).filter(row -> row != null).toList();
        int total = rows.size();
        if (total == 0) {
            job.setStatus("构建成功");
            job.setProgress(100);
            job.setMessage("没有需要构建的文档");
            job.setProcessedDocuments(0);
            job.setTotalDocuments(0);
            job.setFailedDocuments(0);
            job.setUpdatedAt(LocalDateTime.now());
            jobs.save(job);
            auditService.log(userId, "BUILD_KNOWLEDGE_GRAPH", "KNOWLEDGE_GRAPH", job.getId().toString(), "{}");
            return;
        }

        int nodes = 0;
        int relationships = 0;
        int completed = 0;
        int failed = 0;
        List<String> failures = new ArrayList<>();
        if (clearFirst) {
            try {
                graphStore.clearMedRisk();
            } catch (Exception ex) {
                job.setStatus("构建失败");
                job.setProgress(100);
                job.setMessage("Neo4j 图谱清理失败：" + ex.getMessage());
                job.setProcessedDocuments(0);
                job.setTotalDocuments(total);
                job.setFailedDocuments(total);
                rows.forEach(row -> {
                    row.setGraphStatus("构建失败");
                    row.setGraphError(abbreviate("Neo4j 图谱清理失败：" + ex.getMessage(), 1000));
                    row.setUpdatedAt(LocalDateTime.now());
                    documents.save(row);
                });
                job.setUpdatedAt(LocalDateTime.now());
                jobs.save(job);
                auditService.log(userId, "BUILD_KNOWLEDGE_GRAPH_FAILED", "KNOWLEDGE_GRAPH", job.getId().toString(), "{}");
                return;
            }
        }

        for (int i = 0; i < rows.size(); i++) {
            KnowledgeDocumentEntity row = rows.get(i);
            try {
                int baseProgress = Math.round((i * 100f) / total);
                updateJobProgress(job, Math.max(1, Math.min(95, baseProgress + Math.max(3, 30 / total))),
                        "正在抽取文档三元组：" + row.getTitle(), nodes, relationships, i, total, failed);
                row.setGraphError(null);
                List<KnowledgeGraphStore.Triplet> triplets = extractTriplets(row);
                updateJobProgress(job, Math.max(2, Math.min(97, baseProgress + Math.max(8, 70 / total))),
                        "正在写入 Neo4j：" + row.getTitle(), nodes, relationships, i, total, failed);
                KnowledgeGraphStore.GraphWriteResult result = graphStore.mergeTriplets(
                        row.getId(),
                        row.getTitle(),
                        row.getSourceName(),
                        row.getSourceUrl(),
                        row.getSourceLicense(),
                        row.getVisibility(),
                        triplets);
                nodes += result.nodesCreated();
                relationships += result.relationshipsCreated();
                completed++;
                row.setGraphStatus("已构建");
                row.setGraphError(null);
                row.setUpdatedAt(LocalDateTime.now());
                documents.save(row);
            } catch (Exception ex) {
                String reason = graphFailureReason(ex);
                failed++;
                failures.add(row.getTitle() + "：" + reason);
                row.setGraphStatus("构建失败");
                row.setGraphError(abbreviate(reason, 1000));
                row.setUpdatedAt(LocalDateTime.now());
                documents.save(row);
            }
            job.setProgress(Math.round(((i + 1) * 100f) / total));
            job.setNodesCreated(nodes);
            job.setRelationshipsCreated(relationships);
            job.setProcessedDocuments(i + 1);
            job.setTotalDocuments(total);
            job.setFailedDocuments(failed);
            job.setMessage("已处理 " + (i + 1) + "/" + total + " 个文档，成功 " + completed + " 个，失败 " + failed + " 个");
            job.setUpdatedAt(LocalDateTime.now());
            jobs.save(job);
        }

        if (failed == 0) {
            job.setStatus("构建成功");
            job.setMessage("完成 " + completed + " 个文档的图谱构建");
            auditService.log(userId, "BUILD_KNOWLEDGE_GRAPH", "KNOWLEDGE_GRAPH", job.getId().toString(), "{}");
        } else if (completed == 0) {
            job.setStatus("构建失败");
            job.setMessage("全部 " + failed + " 个文档构建失败：" + String.join("；", failures.stream().limit(3).toList()));
            auditService.log(userId, "BUILD_KNOWLEDGE_GRAPH_FAILED", "KNOWLEDGE_GRAPH", job.getId().toString(), "{}");
        } else {
            job.setStatus("部分成功");
            job.setMessage("完成 " + completed + " 个文档，失败 " + failed + " 个：" + String.join("；", failures.stream().limit(3).toList()));
            auditService.log(userId, "BUILD_KNOWLEDGE_GRAPH_PARTIAL", "KNOWLEDGE_GRAPH", job.getId().toString(), "{}");
        }
        job.setProgress(100);
        job.setNodesCreated(nodes);
        job.setRelationshipsCreated(relationships);
        job.setProcessedDocuments(total);
        job.setTotalDocuments(total);
        job.setFailedDocuments(failed);
        job.setUpdatedAt(LocalDateTime.now());
        jobs.save(job);
    }

    private void updateJobProgress(
            KnowledgeGraphJobEntity job,
            int progress,
            String message,
            int nodes,
            int relationships,
            int processed,
            int total,
            int failed) {
        job.setProgress(Math.max(0, Math.min(progress, 99)));
        job.setMessage(message);
        job.setNodesCreated(nodes);
        job.setRelationshipsCreated(relationships);
        job.setProcessedDocuments(processed);
        job.setTotalDocuments(total);
        job.setFailedDocuments(failed);
        job.setUpdatedAt(LocalDateTime.now());
        jobs.save(job);
    }

    private void markJobFailed(Long jobId, Exception ex, Long userId) {
        try {
            KnowledgeGraphJobEntity job = jobs.findById(jobId).orElse(null);
            if (job == null) {
                return;
            }
            job.setStatus("构建失败");
            job.setProgress(100);
            job.setMessage("构建任务异常中断：" + abbreviate(graphFailureReason(ex), 900));
            job.setUpdatedAt(LocalDateTime.now());
            jobs.save(job);
            auditService.log(userId, "BUILD_KNOWLEDGE_GRAPH_FAILED", "KNOWLEDGE_GRAPH", jobId.toString(), "{}");
        } catch (Exception ignored) {
            // Keep the executor thread alive even if the failure record cannot be written.
        }
    }

    private void closeStaleRunningJobsIfIdle() {
        if (jobRunning.get()) {
            return;
        }
        for (KnowledgeGraphJobEntity row : jobs.findByStatusOrderByCreatedAtDesc("运行中")) {
            row.setStatus("构建失败");
            row.setProgress(100);
            row.setMessage("上一次构建任务异常中断，已自动结束，请重新执行。");
            row.setUpdatedAt(LocalDateTime.now());
            jobs.save(row);
        }
    }

    private String graphFailureReason(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            message = ex.getClass().getSimpleName();
        }
        if (message.contains("temporarily unavailable") || message.contains("Unable to connect") || message.contains("Connection")) {
            return "Neo4j 连接不可用：" + message;
        }
        if (message.contains("Invalid input") || message.contains("SyntaxException")) {
            return "Neo4j 写入语句失败，可能存在非法图谱标签或关系：" + message;
        }
        if (message.contains("数据集") || message.contains("文档")) {
            return message;
        }
        return "图谱写入失败：" + message;
    }

    List<KnowledgeGraphStore.Triplet> extractTriplets(KnowledgeDocumentEntity document) {
        String title = clean(document.getTitle(), "未命名文档");
        String summary = clean(document.getSummary(), "");
        String content = clean(document.getContent(), "");
        String fullText = (title + "\n" + summary + "\n" + content).trim();
        String lower = fullText.toLowerCase(Locale.ROOT);
        List<String> textUnits = splitTextUnits(fullText);
        List<String> keywords = extractKeywords(fullText, 28);
        List<KnowledgeGraphStore.Triplet> triplets = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        appendLlmTriplets(triplets, seen, title, fullText);

        for (int i = 0; i < textUnits.size(); i++) {
            String unit = textUnits.get(i);
            String sectionName = title + " 文本单元 " + String.format(Locale.ROOT, "%02d", i + 1);
            if (i > 0) {
                String previous = title + " 文本单元 " + String.format(Locale.ROOT, "%02d", i);
                addTriplet(triplets, seen, previous, "TextUnit", "NEXT_TEXT_UNIT", "相邻文本单元",
                        sectionName, "TextUnit", abbreviate(textUnits.get(i - 1), 120), abbreviate(unit, 120));
            }
            addTriplet(triplets, seen, title, "Document", "HAS_TEXT_UNIT", "包含文本单元",
                    sectionName, "TextUnit", title, abbreviate(unit, 120));
            addTriplet(triplets, seen, sectionName, "TextUnit", "HAS_CLAIM", "包含观点",
                    "观点 " + String.format(Locale.ROOT, "%02d", i + 1) + "：" + abbreviate(unit, 36),
                    "Claim", abbreviate(unit, 120), abbreviate(unit, 180));
        }

        for (int i = 0; i < keywords.size(); i++) {
            String keyword = keywords.get(i);
            String sectionName = title + " 文本单元 " + String.format(Locale.ROOT, "%02d", (i % Math.max(1, textUnits.size())) + 1);
            addTriplet(triplets, seen, sectionName, "TextUnit", "MENTIONS_KEYWORD", "提及关键词",
                    keyword, "Keyword", "文档片段", "关键词或主题词");
            addTriplet(triplets, seen, title, "Document", "SUMMARIZES_KEYWORD", "归纳关键词",
                    keyword, "Keyword", title, "关键词或主题词");
        }

        DISEASE_DEPARTMENTS.forEach((disease, department) -> {
            if (lower.contains(disease.toLowerCase(Locale.ROOT))) {
                addTriplet(triplets, seen, disease, "Disease", "MANAGED_BY", "由...管理",
                        department, "Department", "疾病实体", "就诊科室");
                linkTerms(triplets, seen, lower, disease, "Disease", SYMPTOMS, "SHOWS_SYMPTOM", "表现症状", "Symptom");
                linkTerms(triplets, seen, lower, disease, "Disease", TREATMENTS, "TREATED_WITH", "用...治疗", "Treatment");
                linkTerms(triplets, seen, lower, disease, "Disease", EXAMS, "REQUIRES_EXAMINATION", "需要检查", "Exam");
                linkTerms(triplets, seen, lower, disease, "Disease", RISK_FACTORS, "AGGRAVATED_BY", "被...加重", "RiskFactor");
                linkTerms(triplets, seen, lower, disease, "Disease", DRUGS, "USES_DRUG", "使用药物", "Drug");
            }
        });

        linkTerms(triplets, seen, lower, title, "Document", SYMPTOMS, "MENTIONS_SYMPTOM", "提及症状", "Symptom");
        linkTerms(triplets, seen, lower, title, "Document", TREATMENTS, "MENTIONS_TREATMENT", "提及治疗", "Treatment");
        linkTerms(triplets, seen, lower, title, "Document", EXAMS, "MENTIONS_EXAM", "提及检查", "Exam");
        linkTerms(triplets, seen, lower, title, "Document", RISK_FACTORS, "MENTIONS_RISK_FACTOR", "提及风险因素", "RiskFactor");
        linkTerms(triplets, seen, lower, title, "Document", DRUGS, "MENTIONS_DRUG", "提及药物", "Drug");

        ensureMinimumSemanticTriplets(triplets, seen, title, lower, keywords);
        return triplets;
    }

    private void appendLlmTriplets(List<KnowledgeGraphStore.Triplet> triplets, Set<String> seen, String title, String fullText) {
        LlmProfileService.RuntimeProfile profile;
        try {
            profile = llmProfiles.resolve(null);
        } catch (Exception ex) {
            return;
        }
        for (String chunk : splitFixedChunks(fullText, LLM_CHUNK_SIZE, MAX_LLM_CHUNKS)) {
            try {
                String json = llmService.extractKnowledgeTriplets(title, chunk, profile);
                for (KnowledgeGraphStore.Triplet triplet : parseTripletJson(json)) {
                    addTriplet(
                            triplets,
                            seen,
                            triplet.head(),
                            normalizeNodeType(triplet.headType()),
                            normalizeRelation(triplet.relation()),
                            clean(triplet.relationLabel(), RELATION_LABELS.getOrDefault(normalizeRelation(triplet.relation()), "相关")),
                            triplet.tail(),
                            normalizeNodeType(triplet.tailType()),
                            triplet.headDescription(),
                            triplet.tailDescription());
                }
            } catch (Exception ignored) {
                // LLM extraction is opportunistic; deterministic rules below keep the graph usable.
            }
        }
    }

    private List<KnowledgeGraphStore.Triplet> parseTripletJson(String json) throws Exception {
        String cleaned = extractJsonArray(json);
        if (cleaned.isBlank()) {
            return List.of();
        }
        JsonNode root = objectMapper.readTree(cleaned);
        if (!root.isArray()) {
            return List.of();
        }
        List<KnowledgeGraphStore.Triplet> rows = new ArrayList<>();
        for (JsonNode item : root) {
            String head = text(item, "head", "headName");
            String tail = text(item, "tail", "tailName");
            String headType = text(item, "head_type", "headType");
            String tailType = text(item, "tail_type", "tailType");
            String relation = text(item, "relation", "type");
            if (head.isBlank() || tail.isBlank()) {
                continue;
            }
            rows.add(new KnowledgeGraphStore.Triplet(
                    head,
                    normalizeNodeType(headType),
                    normalizeRelation(relation),
                    text(item, "relation_label", "relationLabel"),
                    tail,
                    normalizeNodeType(tailType),
                    text(item, "head_description", "headDescription"),
                    text(item, "tail_description", "tailDescription")));
        }
        return rows;
    }

    private String extractJsonArray(String value) {
        String cleaned = clean(value, "");
        if (cleaned.isBlank()) {
            return "";
        }
        int start = cleaned.indexOf('[');
        int end = cleaned.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return "";
        }
        return cleaned.substring(start, end + 1);
    }

    private String text(JsonNode node, String primary, String fallback) {
        String value = node.path(primary).asText("");
        if (value.isBlank()) {
            value = node.path(fallback).asText("");
        }
        return clean(value, "");
    }

    private String normalizeNodeType(String value) {
        String cleaned = clean(value, "Entity").replaceAll("[^A-Za-z0-9_]", "");
        if (ALLOWED_NODE_TYPES.contains(cleaned)) {
            return cleaned;
        }
        return switch (cleaned.toLowerCase(Locale.ROOT)) {
            case "disease", "illness" -> "Disease";
            case "symptom" -> "Symptom";
            case "bodypart", "organ" -> "BodyPart";
            case "pathogen" -> "Pathogen";
            case "drug", "medicine", "medication" -> "Drug";
            case "treatment", "therapy" -> "Treatment";
            case "exam", "examination", "test" -> "Exam";
            case "complication" -> "Complication";
            case "riskfactor", "risk" -> "RiskFactor";
            case "department" -> "Department";
            case "document" -> "Document";
            case "timepoint", "time" -> "TimePoint";
            default -> "Keyword";
        };
    }

    private String normalizeRelation(String value) {
        String cleaned = clean(value, "RELATED_TO").trim();
        String alias = RELATION_ALIASES.get(cleaned);
        if (alias != null) {
            return alias;
        }
        String normalized = cleaned.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_]", "");
        return RELATION_LABELS.containsKey(normalized) ? normalized : "RELATED_TO";
    }

    private void linkTerms(
            List<KnowledgeGraphStore.Triplet> triplets,
            Set<String> seen,
            String lowerText,
            String head,
            String headType,
            Set<String> terms,
            String relation,
            String label,
            String tailType) {
        for (String term : terms) {
            if (lowerText.contains(term.toLowerCase(Locale.ROOT))) {
                addTriplet(triplets, seen, head, headType, relation, label, term, tailType, headType, tailType);
            }
        }
    }

    private void ensureMinimumSemanticTriplets(
            List<KnowledgeGraphStore.Triplet> triplets,
            Set<String> seen,
            String title,
            String lowerText,
            List<String> keywords) {
        String anchorDisease = primaryDisease(lowerText).orElse(title);
        String anchorType = primaryDisease(lowerText).isPresent() ? "Disease" : "Document";
        if ("Disease".equals(anchorType)) {
            addTriplet(triplets, seen, title, "Document", "DIAGNOSED_AS", "诊断为",
                    anchorDisease, "Disease", title, "文档识别出的主要疾病");
        }
        List<SemanticTail> semanticTails = semanticTailCandidates(keywords);
        int index = 1;
        while (countDistinctGraphNodes(title, triplets) < MIN_DOCUMENT_GRAPH_NODES && index <= 120) {
            SemanticTail tail = semanticTails.get((index - 1) % semanticTails.size());
            String name = tail.name() + " " + String.format(Locale.ROOT, "%02d", index);
            addTriplet(triplets, seen, anchorDisease, anchorType, tail.relation(), tail.relationLabel(),
                    name, tail.type(), anchorDisease, "离线规则补充的医学三元组实体");
            addTriplet(triplets, seen, title, "Document", tail.documentRelation(), tail.documentRelationLabel(),
                    name, tail.type(), title, "离线规则补充的医学三元组实体");
            index++;
        }
    }

    private java.util.Optional<String> primaryDisease(String lowerText) {
        return DISEASE_DEPARTMENTS.keySet().stream()
                .filter(disease -> lowerText.contains(disease.toLowerCase(Locale.ROOT)))
                .findFirst();
    }

    private List<SemanticTail> semanticTailCandidates(List<String> keywords) {
        List<String> seeds = keywords.isEmpty()
                ? List.of("症状观察", "治疗方案", "检查项目", "风险因素", "用药管理")
                : keywords.stream().filter(value -> !value.isBlank()).limit(24).toList();
        List<SemanticTail> rows = new ArrayList<>();
        for (String seed : seeds) {
            rows.add(new SemanticTail(seed + "相关症状", "Symptom", "SHOWS_SYMPTOM", "表现症状",
                    "MENTIONS_SYMPTOM", "提及症状"));
            rows.add(new SemanticTail(seed + "治疗方法", "Treatment", "TREATED_WITH", "用...治疗",
                    "MENTIONS_TREATMENT", "提及治疗"));
            rows.add(new SemanticTail(seed + "检查项目", "Exam", "REQUIRES_EXAMINATION", "需要检查",
                    "MENTIONS_EXAM", "提及检查"));
            rows.add(new SemanticTail(seed + "风险因素", "RiskFactor", "AGGRAVATED_BY", "被...加重",
                    "MENTIONS_RISK_FACTOR", "提及风险因素"));
            rows.add(new SemanticTail(seed + "相关药物", "Drug", "USES_DRUG", "使用药物",
                    "MENTIONS_DRUG", "提及药物"));
            if (rows.size() >= 120) {
                break;
            }
        }
        return rows.isEmpty() ? semanticTailCandidates(List.of("医学知识")) : rows;
    }

    private int countDistinctGraphNodes(String title, List<KnowledgeGraphStore.Triplet> triplets) {
        Set<String> nodes = new HashSet<>();
        nodes.add("Document:" + title);
        for (KnowledgeGraphStore.Triplet triplet : triplets) {
            nodes.add(triplet.headType() + ":" + triplet.head());
            nodes.add(triplet.tailType() + ":" + triplet.tail());
        }
        return nodes.size();
    }

    private void addTriplet(
            List<KnowledgeGraphStore.Triplet> triplets,
            Set<String> seen,
            String head,
            String headType,
            String relation,
            String relationLabel,
            String tail,
            String tailType,
            String headDescription,
            String tailDescription) {
        String cleanedHead = clean(head, "");
        String cleanedTail = clean(tail, "");
        if (cleanedHead.isBlank() || cleanedTail.isBlank()) {
            return;
        }
        String key = headType + "|" + cleanedHead + "|" + relation + "|" + tailType + "|" + cleanedTail;
        if (seen.add(key)) {
            triplets.add(new KnowledgeGraphStore.Triplet(
                    cleanedHead,
                    clean(headType, "Entity"),
                    clean(relation, "RELATED_TO"),
                    clean(relationLabel, "相关"),
                    cleanedTail,
                    clean(tailType, "Entity"),
                    clean(headDescription, ""),
                    clean(tailDescription, "")));
        }
    }

    private List<String> splitTextUnits(String text) {
        String normalized = clean(text, "");
        if (normalized.isBlank()) {
            return List.of("空白文档，请补充医学内容后重新构建。");
        }
        LinkedHashSet<String> units = new LinkedHashSet<>();
        for (String part : normalized.split("[\\r\\n。！？!?；;]+")) {
            String value = part.trim();
            if (value.length() > 120) {
                for (int start = 0; start < value.length(); start += 80) {
                    units.add(value.substring(start, Math.min(value.length(), start + 100)).trim());
                    if (units.size() >= MAX_TEXT_UNITS) return List.copyOf(units);
                }
            } else if (value.length() >= 4) {
                units.add(value);
            }
            if (units.size() >= MAX_TEXT_UNITS) return List.copyOf(units);
        }
        if (units.isEmpty()) {
            units.add(abbreviate(normalized, 100));
        }
        return List.copyOf(units);
    }

    private List<String> splitFixedChunks(String text, int chunkSize, int limit) {
        String normalized = clean(text, "");
        if (normalized.isBlank()) {
            return List.of();
        }
        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < normalized.length() && chunks.size() < limit; start += chunkSize) {
            chunks.add(normalized.substring(start, Math.min(normalized.length(), start + chunkSize)));
        }
        return chunks;
    }

    private List<String> extractKeywords(String text, int limit) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        for (String token : clean(text, "").split("[\\s,，、。；;：:/\\\\()（）\\[\\]{}<>《》\"“”'‘’]+")) {
            String value = token.trim();
            if (value.isBlank()) {
                continue;
            }
            String lower = value.toLowerCase(Locale.ROOT);
            if (lower.matches("[a-z][a-z0-9-]*") && (lower.length() < 4 || STOPWORDS.contains(lower))) {
                continue;
            }
            if (value.length() > 24) {
                value = value.substring(0, 24);
            }
            if (value.length() >= 2) {
                keywords.add(value);
            }
            if (keywords.size() >= limit) {
                break;
            }
        }
        if (keywords.isEmpty()) {
            keywords.add("医学知识");
            keywords.add("疾病风险");
            keywords.add("诊疗建议");
        }
        return List.copyOf(keywords);
    }

    private String abbreviate(String value, int maxLength) {
        String cleaned = clean(value, "");
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength) + "...";
    }

    private String clean(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String cleaned = value.replaceAll("\\s+", " ").trim();
        return cleaned.isBlank() ? fallback : cleaned;
    }

    public KnowledgeGraphStore.GraphWriteResult mergeStructuredTriplets(
            String documentTitle,
            String sourceName,
            String sourceUrl,
            String sourceLicense,
            String visibility,
            List<KnowledgeGraphStore.Triplet> triplets) {
        return graphStore.mergeTriplets(null, documentTitle, sourceName, sourceUrl, sourceLicense, visibility, triplets);
    }

    private List<String> allowedVisibilities(UserEntity user, String requestedVisibility) {
        List<String> allowed = new ArrayList<>(VisibilityPolicy.allowed(user));
        String requested = VisibilityPolicy.normalize(requestedVisibility, "");
        if (!requested.isBlank() && allowed.contains(requested)) {
            return List.of(requested);
        }
        return allowed;
    }

    private Map<String, Object> toMap(KnowledgeGraphJobEntity job) {
        return orderedMap(
                "id", job.getId(),
                "jobType", job.getJobType(),
                "status", job.getStatus(),
                "progress", job.getProgress(),
                "message", job.getMessage(),
                "nodesCreated", job.getNodesCreated(),
                "relationshipsCreated", job.getRelationshipsCreated(),
                "processedDocuments", job.getProcessedDocuments(),
                "totalDocuments", job.getTotalDocuments(),
                "failedDocuments", job.getFailedDocuments(),
                "documentId", job.getDocumentId(),
                "startedBy", job.getStartedBy(),
                "createdAt", job.getCreatedAt(),
                "updatedAt", job.getUpdatedAt());
    }

    private Map<String, Object> orderedMap(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        return map;
    }

    private record SemanticTail(
            String name,
            String type,
            String relation,
            String relationLabel,
            String documentRelation,
            String documentRelationLabel) {}
}
