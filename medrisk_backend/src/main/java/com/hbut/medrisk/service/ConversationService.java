package com.hbut.medrisk.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbut.medrisk.entity.ConversationEntity;
import com.hbut.medrisk.entity.QaHistoryEntity;
import com.hbut.medrisk.entity.UserEntity;
import com.hbut.medrisk.repository.ConversationRepository;
import com.hbut.medrisk.repository.QaHistoryRepository;
import jakarta.persistence.EntityNotFoundException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ConversationService {
    private final ConversationRepository conversations;
    private final QaHistoryRepository history;
    private final KnowledgeGraphService graphService;
    private final DiseaseInfoService diseaseService;
    private final MedicalCaseService medicalCaseService;
    private final FileStorageService files;
    private final LlmService llm;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public ConversationService(
            ConversationRepository conversations,
            QaHistoryRepository history,
            KnowledgeGraphService graphService,
            DiseaseInfoService diseaseService,
            MedicalCaseService medicalCaseService,
            FileStorageService files,
            LlmService llm,
            AuditService auditService,
            ObjectMapper objectMapper) {
        this.conversations = conversations;
        this.history = history;
        this.graphService = graphService;
        this.diseaseService = diseaseService;
        this.medicalCaseService = medicalCaseService;
        this.files = files;
        this.llm = llm;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> list(UserEntity user) {
        return conversations.findTop100ByUserIdOrderByUpdatedAtDesc(user.getId()).stream().map(this::toConversationMap).toList();
    }

    @Transactional
    public Map<String, Object> create(String title, UserEntity user) {
        ConversationEntity row = new ConversationEntity();
        row.setTitle(clean(title).isBlank() ? "新的医疗问答" : clean(title));
        row.setUserId(user.getId());
        row.setUserName(user.getName());
        LocalDateTime now = LocalDateTime.now();
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        conversations.save(row);
        auditService.log(user.getId(), "CREATE_CONVERSATION", "CONVERSATION", row.getId().toString(), "{}");
        return detail(row, user);
    }

    public Map<String, Object> get(Long id, UserEntity user) {
        return detail(requireAccessible(id, user), user);
    }

    @Transactional
    public Map<String, Object> delete(Long id, UserEntity user) {
        ConversationEntity row = requireAccessible(id, user);
        conversations.delete(row);
        auditService.log(user.getId(), "DELETE_CONVERSATION", "CONVERSATION", id.toString(), "{}");
        return Map.of("deleted", true, "id", id);
    }

    @Transactional
    public Map<String, Object> ask(Long conversationId, String question, MultipartFile image, UserEntity user) throws IOException {
        String cleanedQuestion = clean(question);
        if (cleanedQuestion.isBlank()) {
            throw new IllegalArgumentException("问题不能为空");
        }
        ConversationEntity conversation = requireAccessible(conversationId, user);
        FileStorageService.StoredFile storedImage = null;
        byte[] imageBytes = null;
        if (image != null && !image.isEmpty()) {
            imageBytes = image.getBytes();
            storedImage = files.store("qa-images", image);
        }

        List<String> keywords = keywords(cleanedQuestion);
        List<Map<String, Object>> graphMatches = new ArrayList<>();
        for (String keyword : keywords) {
            graphMatches.addAll(graphService.search(keyword));
        }
        List<Map<String, Object>> diseaseMatches = new ArrayList<>(diseaseService.search(cleanedQuestion, user));
        if (diseaseMatches.isEmpty()) {
            for (String keyword : keywords) diseaseMatches.addAll(diseaseService.search(keyword, user));
        }
        List<Map<String, Object>> caseMatches = new ArrayList<>(medicalCaseService.search(cleanedQuestion, user));
        if (caseMatches.isEmpty()) {
            for (String keyword : keywords) caseMatches.addAll(medicalCaseService.search(keyword, user));
        }

        String context = buildContext(graphMatches, diseaseMatches, caseMatches);
        String answer = llm.answer(cleanedQuestion, context, imageBytes, image == null ? null : image.getContentType());

        QaHistoryEntity qa = new QaHistoryEntity();
        qa.setConversationId(conversation.getId());
        qa.setQuestion(cleanedQuestion);
        qa.setAnswer(answer);
        qa.setRelatedEntitiesJson(toJson(graphMatches.stream().limit(12).toList()));
        qa.setGraphContextJson(toJson(graphMatches));
        qa.setDiseaseInfoMatchesJson(toJson(diseaseMatches.stream().limit(8).toList()));
        qa.setDiseaseCaseMatchesJson(toJson(caseMatches.stream().limit(8).toList()));
        qa.setKeywordsJson(toJson(keywords));
        if (storedImage != null) {
            qa.setImageBucket(storedImage.bucket());
            qa.setImageObjectKey(storedImage.objectKey());
            qa.setImageUrl(storedImage.url());
        }
        qa.setUserId(user.getId());
        qa.setUserName(user.getName());
        qa.setCreatedAt(LocalDateTime.now());
        history.save(qa);

        if ("新的医疗问答".equals(conversation.getTitle())) {
            conversation.setTitle(cleanedQuestion.length() > 30 ? cleanedQuestion.substring(0, 30) + "…" : cleanedQuestion);
        }
        conversation.setUpdatedAt(LocalDateTime.now());
        auditService.log(user.getId(), "ASK_MEDICAL_QA", "QA_HISTORY", qa.getId().toString(), "{}");
        return toQaMap(qa);
    }

    private ConversationEntity requireAccessible(Long id, UserEntity user) {
        ConversationEntity row = conversations.findById(id).orElseThrow(() -> new EntityNotFoundException("会话不存在"));
        if (!row.getUserId().equals(user.getId()) && "PATIENT".equals(user.getRole())) {
            throw new SecurityException("不能访问其他用户的会话");
        }
        if (!row.getUserId().equals(user.getId()) && "DOCTOR".equals(user.getRole())) {
            throw new SecurityException("不能访问其他用户的会话");
        }
        return row;
    }

    private Map<String, Object> detail(ConversationEntity row, UserEntity user) {
        return orderedMap(
                "conversation", toConversationMap(row),
                "messages", history.findByConversationIdOrderByCreatedAtAsc(row.getId()).stream().map(this::toQaMap).toList());
    }

    private Map<String, Object> toConversationMap(ConversationEntity row) {
        return orderedMap(
                "id", row.getId(),
                "title", row.getTitle(),
                "userId", row.getUserId(),
                "userName", row.getUserName(),
                "createdAt", row.getCreatedAt(),
                "updatedAt", row.getUpdatedAt());
    }

    private Map<String, Object> toQaMap(QaHistoryEntity row) {
        return orderedMap(
                "id", row.getId(),
                "conversationId", row.getConversationId(),
                "question", row.getQuestion(),
                "answer", row.getAnswer(),
                "relatedEntities", readJson(row.getRelatedEntitiesJson()),
                "graphContext", readJson(row.getGraphContextJson()),
                "diseaseInfoMatches", readJson(row.getDiseaseInfoMatchesJson()),
                "diseaseCaseMatches", readJson(row.getDiseaseCaseMatchesJson()),
                "keywords", readJson(row.getKeywordsJson()),
                "imageUrl", row.getImageUrl(),
                "createdAt", row.getCreatedAt());
    }

    private List<String> keywords(String question) {
        Set<String> values = new LinkedHashSet<>();
        Arrays.stream(question.split("[\\s,，。；;、：:？?！!]+"))
                .map(String::trim)
                .filter(token -> token.length() >= 2)
                .limit(8)
                .forEach(values::add);
        List.of("糖尿病", "高血压", "冠心病", "肺炎", "胃炎", "心脏病", "慢性肾病", "肝病", "中风", "骨折", "发热", "胸痛", "咳嗽")
                .forEach(value -> {
                    if (question.toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT))) values.add(value);
                });
        if (values.isEmpty()) {
            values.add(question.length() > 20 ? question.substring(0, 20) : question);
        }
        return new ArrayList<>(values).stream().limit(10).toList();
    }

    private String buildContext(List<Map<String, Object>> graph, List<Map<String, Object>> diseases, List<Map<String, Object>> cases) {
        StringBuilder context = new StringBuilder();
        if (!graph.isEmpty()) {
            context.append("【知识图谱实体】\n");
            graph.stream().limit(10).forEach(row -> context.append("- ")
                    .append(row.get("name")).append(" (").append(row.get("type")).append(") ")
                    .append(row.getOrDefault("description", "")).append('\n'));
        }
        if (!diseases.isEmpty()) {
            context.append("【疾病档案】\n");
            diseases.stream().limit(6).forEach(row -> context.append("- ")
                    .append(row.get("diseaseName")).append("：科室 ").append(row.getOrDefault("department", ""))
                    .append("；症状 ").append(row.getOrDefault("symptoms", ""))
                    .append("；治疗 ").append(row.getOrDefault("treatmentPlan", "")).append('\n'));
        }
        if (!cases.isEmpty()) {
            context.append("【病历案例】\n");
            cases.stream().limit(5).forEach(row -> context.append("- ")
                    .append(row.get("caseTitle")).append("：诊断 ").append(row.getOrDefault("diagnosis", ""))
                    .append("；治疗 ").append(row.getOrDefault("treatmentGiven", "")).append('\n'));
        }
        return context.toString();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private Object readJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private Map<String, Object> orderedMap(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        return map;
    }
}
