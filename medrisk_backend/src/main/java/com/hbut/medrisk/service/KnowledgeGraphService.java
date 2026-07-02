package com.hbut.medrisk.service;

import com.hbut.medrisk.entity.KnowledgeDocumentEntity;
import com.hbut.medrisk.entity.KnowledgeGraphJobEntity;
import com.hbut.medrisk.entity.UserEntity;
import com.hbut.medrisk.repository.KnowledgeDocumentRepository;
import com.hbut.medrisk.repository.KnowledgeGraphJobRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeGraphService {
    private static final int MIN_DOCUMENT_GRAPH_NODES = 50;
    private static final int MAX_TEXT_UNITS = 18;
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

    private final KnowledgeGraphStore graphStore;
    private final KnowledgeDocumentRepository documents;
    private final KnowledgeGraphJobRepository jobs;
    private final AuditService auditService;

    public KnowledgeGraphService(
            KnowledgeGraphStore graphStore,
            KnowledgeDocumentRepository documents,
            KnowledgeGraphJobRepository jobs,
            AuditService auditService) {
        this.graphStore = graphStore;
        this.documents = documents;
        this.jobs = jobs;
        this.auditService = auditService;
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
        return runJob("单文档同步", document, List.of(document), false, user);
    }

    @Transactional
    public Map<String, Object> incremental(UserEntity user) {
        List<KnowledgeDocumentEntity> pending = documents.findByGraphStatusInOrderByCreatedAtAsc(List.of("未构建", "构建失败"));
        return runJob("增量构建", null, pending, false, user);
    }

    @Transactional
    public Map<String, Object> rebuild(UserEntity user) {
        List<KnowledgeDocumentEntity> all = documents.findAllNewest();
        return runJob("全量重建", null, all, true, user);
    }

    private Map<String, Object> runJob(String type, KnowledgeDocumentEntity document, List<KnowledgeDocumentEntity> rows, boolean clearFirst, UserEntity user) {
        KnowledgeGraphJobEntity job = new KnowledgeGraphJobEntity();
        job.setJobType(type);
        job.setStatus("运行中");
        job.setProgress(0);
        job.setMessage("准备构建知识图谱");
        job.setNodesCreated(0);
        job.setRelationshipsCreated(0);
        job.setDocumentId(document == null ? null : document.getId());
        job.setStartedBy(user.getId());
        LocalDateTime now = LocalDateTime.now();
        job.setCreatedAt(now);
        job.setUpdatedAt(now);
        jobs.save(job);
        int total = rows.size();
        if (total == 0) {
            job.setStatus("构建成功");
            job.setProgress(100);
            job.setMessage("没有需要构建的文档");
            job.setUpdatedAt(LocalDateTime.now());
            jobs.save(job);
            auditService.log(user.getId(), "BUILD_KNOWLEDGE_GRAPH", "KNOWLEDGE_GRAPH", job.getId().toString(), "{}");
            return toMap(job);
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
                rows.forEach(row -> {
                    row.setGraphStatus("构建失败");
                    row.setGraphError(abbreviate("Neo4j 图谱清理失败：" + ex.getMessage(), 1000));
                    row.setUpdatedAt(LocalDateTime.now());
                });
                job.setUpdatedAt(LocalDateTime.now());
                jobs.save(job);
                auditService.log(user.getId(), "BUILD_KNOWLEDGE_GRAPH_FAILED", "KNOWLEDGE_GRAPH", job.getId().toString(), "{}");
                return toMap(job);
            }
        }

        for (int i = 0; i < rows.size(); i++) {
            KnowledgeDocumentEntity row = rows.get(i);
            try {
                row.setGraphError(null);
                List<KnowledgeGraphStore.Triplet> triplets = extractTriplets(row);
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
            } catch (Exception ex) {
                String reason = graphFailureReason(ex);
                failed++;
                failures.add(row.getTitle() + "：" + reason);
                row.setGraphStatus("构建失败");
                row.setGraphError(abbreviate(reason, 1000));
                row.setUpdatedAt(LocalDateTime.now());
            }
            job.setProgress(Math.round(((i + 1) * 100f) / total));
            job.setNodesCreated(nodes);
            job.setRelationshipsCreated(relationships);
            job.setMessage("已处理 " + (i + 1) + "/" + total + " 个文档，成功 " + completed + " 个，失败 " + failed + " 个");
            job.setUpdatedAt(LocalDateTime.now());
            jobs.save(job);
        }

        if (failed == 0) {
            job.setStatus("构建成功");
            job.setMessage("完成 " + completed + " 个文档的图谱构建");
            auditService.log(user.getId(), "BUILD_KNOWLEDGE_GRAPH", "KNOWLEDGE_GRAPH", job.getId().toString(), "{}");
        } else if (completed == 0) {
            job.setStatus("构建失败");
            job.setMessage("全部 " + failed + " 个文档构建失败：" + String.join("；", failures.stream().limit(3).toList()));
            auditService.log(user.getId(), "BUILD_KNOWLEDGE_GRAPH_FAILED", "KNOWLEDGE_GRAPH", job.getId().toString(), "{}");
        } else {
            job.setStatus("部分成功");
            job.setMessage("完成 " + completed + " 个文档，失败 " + failed + " 个：" + String.join("；", failures.stream().limit(3).toList()));
            auditService.log(user.getId(), "BUILD_KNOWLEDGE_GRAPH_PARTIAL", "KNOWLEDGE_GRAPH", job.getId().toString(), "{}");
        }
        job.setProgress(100);
        job.setNodesCreated(nodes);
        job.setRelationshipsCreated(relationships);
        job.setUpdatedAt(LocalDateTime.now());
        jobs.save(job);
        return toMap(job);
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

        for (int i = 0; i < textUnits.size(); i++) {
            String unit = textUnits.get(i);
            String sectionName = title + " 文本单元 " + String.format(Locale.ROOT, "%02d", i + 1);
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

        ensureMinimumDocumentNodes(triplets, seen, title, textUnits, keywords);
        return triplets;
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

    private void ensureMinimumDocumentNodes(
            List<KnowledgeGraphStore.Triplet> triplets,
            Set<String> seen,
            String title,
            List<String> textUnits,
            List<String> keywords) {
        List<String> anchors = textUnits.isEmpty() ? List.of(title) : textUnits;
        int index = 1;
        while (countDistinctGraphNodes(title, triplets) < MIN_DOCUMENT_GRAPH_NODES && index <= 120) {
            String sectionName = title + " 文本单元 " + String.format(Locale.ROOT, "%02d", ((index - 1) % Math.max(1, anchors.size())) + 1);
            String keyword = keywords.isEmpty()
                    ? "文档索引 " + String.format(Locale.ROOT, "%02d", index)
                    : keywords.get((index - 1) % keywords.size()) + " 索引 " + String.format(Locale.ROOT, "%02d", index);
            addTriplet(triplets, seen, sectionName, "TextUnit", "HAS_ENRICHED_INDEX", "补充索引",
                    keyword, "Keyword", "文档片段", "离线规则补充的检索节点");
            index++;
        }
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
}
