package com.hbut.medrisk.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbut.medrisk.dto.ConversationStreamRequest;
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
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class ConversationService {
    private static final String CHAT_MODE_DAILY = "daily";
    private static final String CHAT_MODE_MEDICAL = "medical";
    private static final String RETRIEVAL_NOT_REQUESTED = "not_requested";
    private static final String RETRIEVAL_SUCCESS = "success";
    private static final String RETRIEVAL_DEGRADED = "degraded";
    private static final String POLICY_GUARD_ANSWER = """
            本系统仅回答 MedRisk 医疗平台、健康风险预测、医学知识库和教学演示相关问题。
            请围绕疾病风险、症状、检查、治疗、药物、病历、文档、知识图谱、模型训练评估或平台使用提问。

            本回答仅用于教学演示和健康知识参考，不能替代医生诊断。""";
    private static final int MAX_STREAM_IMAGE_BYTES = 8 * 1024 * 1024;

    private final ConversationRepository conversations;
    private final QaHistoryRepository history;
    private final KnowledgeGraphService graphService;
    private final DiseaseInfoService diseaseService;
    private final MedicalCaseService medicalCaseService;
    private final FileStorageService files;
    private final LlmService llm;
    private final LlmProfileService llmProfiles;
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
            LlmProfileService llmProfiles,
            AuditService auditService,
            ObjectMapper objectMapper) {
        this.conversations = conversations;
        this.history = history;
        this.graphService = graphService;
        this.diseaseService = diseaseService;
        this.medicalCaseService = medicalCaseService;
        this.files = files;
        this.llm = llm;
        this.llmProfiles = llmProfiles;
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
    public Map<String, Object> ask(
            Long conversationId,
            String question,
            MultipartFile image,
            Long modelProfileId,
            Boolean reasoningRequested,
            String chatMode,
            Boolean outputImageRequested,
            UserEntity user) throws IOException {
        String cleanedQuestion = clean(question);
        if (cleanedQuestion.isBlank()) {
            throw new IllegalArgumentException("问题不能为空");
        }
        ConversationEntity conversation = requireAccessible(conversationId, user);
        String normalizedChatMode = normalizeChatMode(chatMode);
        if (!isMedicalPlatformQuestion(cleanedQuestion)) {
            return savePolicyGuardAnswer(conversation, cleanedQuestion, user);
        }
        FileStorageService.StoredFile storedImage = null;
        byte[] imageBytes = null;
        if (image != null && !image.isEmpty()) {
            imageBytes = image.getBytes();
            storedImage = files.store("qa-images", image);
        }

        List<String> keywords = keywords(cleanedQuestion);
        List<Map<String, Object>> graphMatches = new ArrayList<>();
        try {
            for (String keyword : keywords) {
                graphMatches.addAll(graphService.search(keyword));
            }
        } catch (Exception ignored) {
            graphMatches = new ArrayList<>();
        }
        List<Map<String, Object>> diseaseMatches = new ArrayList<>(diseaseService.search(cleanedQuestion, user));
        if (diseaseMatches.isEmpty()) {
            for (String keyword : keywords) diseaseMatches.addAll(diseaseService.search(keyword, user));
        }
        List<Map<String, Object>> caseMatches = new ArrayList<>(medicalCaseService.search(cleanedQuestion, user));
        if (caseMatches.isEmpty()) {
            for (String keyword : keywords) caseMatches.addAll(medicalCaseService.search(keyword, user));
        }

        List<QaHistoryEntity> previousMessages = history.findByConversationIdOrderByCreatedAtAsc(conversation.getId());
        String context = buildContext(graphMatches, diseaseMatches, caseMatches, previousMessages);
        List<Map<String, Object>> evidenceSources = evidenceSources(graphMatches, diseaseMatches, caseMatches);
        LlmProfileService.RuntimeProfile profile = llmProfiles.resolve(modelProfileId);
        boolean reasoningEnabled = Boolean.TRUE.equals(reasoningRequested) && profile.reasoningSupported();
        LlmService.LlmAnswer llmAnswer = llm.answerDetailed(cleanedQuestion, context, imageBytes, image == null ? null : image.getContentType(), profile, reasoningEnabled);
        List<String> generatedImages = List.of();
        if (Boolean.TRUE.equals(outputImageRequested)) {
            LlmService.ImageGenerationResult imageResult = llm.generateImage(cleanedQuestion, imageBytes, image == null ? null : image.getContentType(), profile);
            generatedImages = imageResult.imageUrls();
            llmAnswer = appendGeneratedImages(llmAnswer, generatedImages, imageResult.fallbackUsed());
        }

        QaHistoryEntity qa = new QaHistoryEntity();
        qa.setConversationId(conversation.getId());
        qa.setQuestion(cleanedQuestion);
        qa.setAnswer(llmAnswer.answer());
        qa.setRelatedEntitiesJson(toJson(graphMatches.stream().limit(12).toList()));
        qa.setGraphContextJson(toJson(graphMatches));
        qa.setDiseaseInfoMatchesJson(toJson(diseaseMatches.stream().limit(8).toList()));
        qa.setDiseaseCaseMatchesJson(toJson(caseMatches.stream().limit(8).toList()));
        qa.setKeywordsJson(toJson(keywords));
        qa.setUsedModel(llmAnswer.usedModel());
        qa.setProvider(llmAnswer.provider());
        qa.setModelProfileId(profile.id());
        qa.setChatMode(normalizedChatMode);
        qa.setRetrievalUsed(true);
        qa.setRetrievalStatus(retrievalStatus(graphMatches, diseaseMatches, caseMatches));
        qa.setReasoningEnabled(reasoningEnabled);
        qa.setReasoningContent(llmAnswer.reasoningContent());
        qa.setFallbackUsed(llmAnswer.fallbackUsed());
        qa.setEvidenceSourcesJson(toJson(evidenceSources));
        qa.setGeneratedImagesJson(toJson(generatedImages));
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

    public SseEmitter streamAsk(Long conversationId, ConversationStreamRequest request, UserEntity user, String clientIp) {
        SseEmitter emitter = new SseEmitter(120_000L);
        CompletableFuture.runAsync(() -> {
            String cleanedQuestion = clean(request == null ? "" : request.question());
            try {
                if (cleanedQuestion.isBlank()) {
                    sendEvent(emitter, "error", orderedMap("message", "问题不能为空"));
                    emitter.complete();
                    return;
                }
                ConversationEntity conversation = requireAccessible(conversationId, user);
                StreamImage streamImage = decodeStreamImage(request);
                FileStorageService.StoredFile storedImage = null;
                if (streamImage.bytes() != null) {
                    storedImage = files.storeBytes("qa-images", streamImage.filename(), streamImage.contentType(), streamImage.bytes());
                }
                String chatMode = autoChatMode(request == null ? null : request.chatMode(), cleanedQuestion, streamImage.bytes() != null);
                boolean medicalMode = CHAT_MODE_MEDICAL.equals(chatMode);
                sendEvent(emitter, "accepted", orderedMap(
                        "conversationId", conversation.getId(),
                        "question", cleanedQuestion,
                        "chatMode", chatMode,
                        "imageProvided", streamImage.bytes() != null,
                        "imageUrl", storedImage == null ? null : storedImage.url()));
                if (medicalMode && streamImage.bytes() == null && !isMedicalPlatformQuestion(cleanedQuestion)) {
                    QaHistoryEntity qa = savePolicyGuardEntity(conversation, cleanedQuestion, user, clientIp);
                    emitAnswerChunks(emitter, qa.getAnswer());
                    sendEvent(emitter, "metadata", toQaMap(qa));
                    sendEvent(emitter, "done", toQaMap(qa));
                    emitter.complete();
                    return;
                }

                List<QaHistoryEntity> previousMessages = history.findByConversationIdOrderByCreatedAtAsc(conversation.getId());
                Long requestedModelProfileId = request == null ? null : request.modelProfileId();
                LlmProfileService.RuntimeProfile profile = llmProfiles.resolve(requestedModelProfileId);
                boolean reasoningEnabled = Boolean.TRUE.equals(request == null ? null : request.reasoningEnabled()) && profile.reasoningSupported();

                if (!medicalMode) {
                    String context = buildRecentConversationContext(previousMessages);
                    boolean outputImageRequested = shouldGenerateImage(cleanedQuestion, request);
                    sendEvent(emitter, "metadata", orderedMap(
                            "modelProfileId", profile.id(),
                            "usedModel", profile.modelName(),
                            "provider", profile.provider(),
                            "reasoningEnabled", reasoningEnabled,
                            "chatMode", CHAT_MODE_DAILY,
                            "retrievalUsed", false,
                            "retrievalStatus", RETRIEVAL_NOT_REQUESTED,
                            "outputImageRequested", outputImageRequested));
                    LlmService.LlmAnswer llmAnswer = llm.streamDailyChat(
                            cleanedQuestion,
                            context,
                            profile,
                            reasoningEnabled,
                            chunk -> sendEvent(emitter, "reasoning", orderedMap("chunk", chunk)),
                            chunk -> sendEvent(emitter, "answer", orderedMap("chunk", chunk)));
                    List<String> generatedImages = List.of();
                    if (outputImageRequested) {
                        generatedImages = generateImagesForStream(cleanedQuestion, null, null, profile, emitter);
                        llmAnswer = appendGeneratedImages(llmAnswer, generatedImages, generatedImages.isEmpty());
                    }
                    QaHistoryEntity qa = saveQaEntity(
                            conversation,
                            cleanedQuestion,
                            llmAnswer,
                            List.of(),
                            List.of(),
                            List.of(),
                            keywords(cleanedQuestion),
                            List.of(),
                            storedImage,
                            profile.id(),
                            reasoningEnabled,
                            CHAT_MODE_DAILY,
                            false,
                            RETRIEVAL_NOT_REQUESTED,
                            generatedImages,
                            user);
                    auditService.log(user.getId(), "ASK_DAILY_CHAT", "QA_HISTORY", qa.getId().toString(), "{}", clientIp);
                    sendEvent(emitter, "metadata", toQaMap(qa));
                    sendEvent(emitter, "done", toQaMap(qa));
                    emitter.complete();
                    return;
                }

                List<String> keywords = keywords(cleanedQuestion);
                List<Map<String, Object>> graphMatches = graphMatches(keywords);
                List<Map<String, Object>> diseaseMatches = diseaseMatches(cleanedQuestion, keywords, user);
                List<Map<String, Object>> caseMatches = caseMatches(cleanedQuestion, keywords, user);
                String context = buildContext(graphMatches, diseaseMatches, caseMatches, previousMessages);
                List<Map<String, Object>> evidenceSources = evidenceSources(graphMatches, diseaseMatches, caseMatches);
                String retrievalStatus = retrievalStatus(graphMatches, diseaseMatches, caseMatches);
                boolean outputImageRequested = shouldGenerateImage(cleanedQuestion, request);
                String initialModelName = streamImage.bytes() == null ? profile.modelName() : llm.visionModelName(profile);
                sendEvent(emitter, "metadata", orderedMap(
                        "modelProfileId", profile.id(),
                        "usedModel", initialModelName,
                        "provider", profile.provider(),
                        "reasoningEnabled", reasoningEnabled,
                        "chatMode", CHAT_MODE_MEDICAL,
                        "retrievalUsed", true,
                        "retrievalStatus", retrievalStatus,
                        "outputImageRequested", outputImageRequested,
                        "imageProvided", streamImage.bytes() != null,
                        "imageUrl", storedImage == null ? null : storedImage.url()));

                LlmService.LlmAnswer llmAnswer = llm.streamAnswer(
                        cleanedQuestion,
                        context,
                        profile,
                        reasoningEnabled,
                        streamImage.bytes(),
                        streamImage.contentType(),
                        chunk -> sendEvent(emitter, "reasoning", orderedMap("chunk", chunk)),
                        chunk -> sendEvent(emitter, "answer", orderedMap("chunk", chunk)));
                List<String> generatedImages = List.of();
                if (outputImageRequested) {
                    generatedImages = generateImagesForStream(cleanedQuestion, streamImage.bytes(), streamImage.contentType(), profile, emitter);
                    llmAnswer = appendGeneratedImages(llmAnswer, generatedImages, generatedImages.isEmpty());
                }

                QaHistoryEntity qa = saveQaEntity(
                        conversation,
                        cleanedQuestion,
                        llmAnswer,
                        graphMatches,
                        diseaseMatches,
                        caseMatches,
                        keywords,
                        evidenceSources,
                        storedImage,
                        profile.id(),
                        reasoningEnabled,
                        CHAT_MODE_MEDICAL,
                        true,
                        retrievalStatus,
                        generatedImages,
                        user);
                auditService.log(user.getId(), "ASK_MEDICAL_QA", "QA_HISTORY", qa.getId().toString(), "{}", clientIp);
                sendEvent(emitter, "metadata", toQaMap(qa));
                sendEvent(emitter, "done", toQaMap(qa));
                emitter.complete();
            } catch (Exception ex) {
                sendEvent(emitter, "error", orderedMap("message", "问答发送失败，请稍后重试"));
                emitter.complete();
            }
        });
        return emitter;
    }

    private Map<String, Object> savePolicyGuardAnswer(ConversationEntity conversation, String question, UserEntity user) {
        return toQaMap(savePolicyGuardEntity(conversation, question, user, RequestIpContext.get()));
    }

    private QaHistoryEntity savePolicyGuardEntity(ConversationEntity conversation, String question, UserEntity user, String clientIp) {
        QaHistoryEntity qa = new QaHistoryEntity();
        qa.setConversationId(conversation.getId());
        qa.setQuestion(question);
        qa.setAnswer(POLICY_GUARD_ANSWER);
        qa.setRelatedEntitiesJson("[]");
        qa.setGraphContextJson("[]");
        qa.setDiseaseInfoMatchesJson("[]");
        qa.setDiseaseCaseMatchesJson("[]");
        qa.setKeywordsJson(toJson(keywords(question)));
        qa.setUsedModel("policy-guard");
        qa.setProvider("medrisk-policy");
        qa.setModelProfileId(null);
        qa.setChatMode(CHAT_MODE_MEDICAL);
        qa.setRetrievalUsed(false);
        qa.setRetrievalStatus("blocked");
        qa.setReasoningEnabled(false);
        qa.setReasoningContent("");
        qa.setFallbackUsed(true);
        qa.setEvidenceSourcesJson("[]");
        qa.setGeneratedImagesJson("[]");
        qa.setUserId(user.getId());
        qa.setUserName(user.getName());
        qa.setCreatedAt(LocalDateTime.now());
        history.save(qa);

        if ("新的医疗问答".equals(conversation.getTitle())) {
            conversation.setTitle(question.length() > 30 ? question.substring(0, 30) + "…" : question);
        }
        conversation.setUpdatedAt(LocalDateTime.now());
        auditService.log(user.getId(), "ASK_MEDICAL_QA_BLOCKED", "QA_HISTORY", qa.getId().toString(), "{\"reason\":\"out_of_scope\"}", clientIp);
        return qa;
    }

    private boolean isMedicalPlatformQuestion(String question) {
        String normalized = clean(question).toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return false;
        }
        return List.of(
                "medrisk", "medical", "medicine", "health", "disease", "risk", "prediction", "symptom",
                "treatment", "drug", "patient", "doctor", "report", "graph", "knowledge", "document",
                "case", "model", "training", "dataset", "login", "register", "password", "email",
                "医疗", "医学", "健康", "疾病", "症状", "检查", "治疗", "药", "用药", "风险", "预测",
                "报告", "图谱", "知识库", "病历", "病例", "文档", "模型", "训练", "评估", "数据集",
                "患者", "医生", "管理员", "平台", "系统", "登录", "注册", "密码", "邮箱", "验证码",
                "上传", "构建", "节点", "关系", "问答", "对话", "功能", "帮助", "怎么用", "使用",
                "糖尿病", "高血压", "冠心病", "心脏", "肾", "肝", "中风", "肺炎", "胃炎", "骨折",
                "发热", "胸痛", "咳嗽", "体检", "科室", "筛查", "诊断", "随访", "处方", "禁忌",
                "neo4j", "xgboost", "tabpfn", "tabicl", "知识图谱", "智能问答", "你能做什么", "你是谁")
                .stream()
                .anyMatch(normalized::contains);
    }

    private String normalizeChatMode(String value) {
        return CHAT_MODE_DAILY.equalsIgnoreCase(clean(value)) ? CHAT_MODE_DAILY : CHAT_MODE_MEDICAL;
    }

    private String autoChatMode(String requested, String question, boolean imageProvided) {
        String explicit = clean(requested).toLowerCase(Locale.ROOT);
        if (imageProvided) {
            return CHAT_MODE_MEDICAL;
        }
        if (CHAT_MODE_DAILY.equals(explicit) || CHAT_MODE_MEDICAL.equals(explicit)) {
            return explicit;
        }
        return isMedicalPlatformQuestion(question) ? CHAT_MODE_MEDICAL : CHAT_MODE_DAILY;
    }

    private boolean shouldGenerateImage(String question, ConversationStreamRequest request) {
        if (Boolean.TRUE.equals(request == null ? null : request.outputImageRequested())) {
            return true;
        }
        String normalized = clean(question).toLowerCase(Locale.ROOT);
        return List.of("生成图片", "输出图片", "画图", "配图", "示意图", "流程图", "可视化图片", "generate image", "draw", "picture", "image")
                .stream()
                .anyMatch(normalized::contains);
    }

    private StreamImage decodeStreamImage(ConversationStreamRequest request) {
        if (request == null || request.imageBase64() == null || request.imageBase64().isBlank()) {
            return new StreamImage(null, null, null);
        }
        String value = request.imageBase64().trim();
        String contentType = request.imageContentType() == null || request.imageContentType().isBlank()
                ? "image/jpeg"
                : request.imageContentType().trim();
        int comma = value.indexOf(',');
        if (value.startsWith("data:") && comma > 0) {
            String header = value.substring(5, comma);
            int semicolon = header.indexOf(';');
            if (semicolon > 0) {
                contentType = header.substring(0, semicolon);
            }
            value = value.substring(comma + 1);
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("图片编码不合法，请重新上传图片");
        }
        if (bytes.length > MAX_STREAM_IMAGE_BYTES) {
            throw new IllegalArgumentException("图片过大，请上传 8MB 以内的图片");
        }
        if (!contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new IllegalArgumentException("仅支持图片输入");
        }
        return new StreamImage(bytes, contentType, "qa-image" + imageExtension(contentType));
    }

    private String imageExtension(String contentType) {
        String lower = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (lower.contains("png")) return ".png";
        if (lower.contains("webp")) return ".webp";
        if (lower.contains("gif")) return ".gif";
        return ".jpg";
    }

    private String retrievalStatus(List<Map<String, Object>> graph, List<Map<String, Object>> diseases, List<Map<String, Object>> cases) {
        return graph.isEmpty() && diseases.isEmpty() && cases.isEmpty() ? RETRIEVAL_DEGRADED : RETRIEVAL_SUCCESS;
    }

    private String buildRecentConversationContext(List<QaHistoryEntity> previousMessages) {
        if (previousMessages.isEmpty()) {
            return "无";
        }
        StringBuilder context = new StringBuilder();
        previousMessages.stream()
                .skip(Math.max(0, previousMessages.size() - 8))
                .forEach(row -> context.append("- 用户：").append(truncate(row.getQuestion(), 160))
                        .append("\n  助手：").append(truncate(row.getAnswer(), 240)).append('\n'));
        return context.toString();
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
                "usedModel", row.getUsedModel(),
                "provider", row.getProvider(),
                "modelProfileId", row.getModelProfileId(),
                "chatMode", row.getChatMode() == null ? CHAT_MODE_MEDICAL : row.getChatMode(),
                "retrievalUsed", Boolean.TRUE.equals(row.getRetrievalUsed()),
                "retrievalStatus", row.getRetrievalStatus() == null ? RETRIEVAL_SUCCESS : row.getRetrievalStatus(),
                "reasoningEnabled", Boolean.TRUE.equals(row.getReasoningEnabled()),
                "reasoningContent", row.getReasoningContent(),
                "fallbackUsed", Boolean.TRUE.equals(row.getFallbackUsed()),
                "evidenceSources", readJson(row.getEvidenceSourcesJson()),
                "generatedImages", readJson(row.getGeneratedImagesJson()),
                "imageUrl", row.getImageUrl(),
                "createdAt", row.getCreatedAt());
    }

    private QaHistoryEntity saveQaEntity(
            ConversationEntity conversation,
            String question,
            LlmService.LlmAnswer llmAnswer,
            List<Map<String, Object>> graphMatches,
            List<Map<String, Object>> diseaseMatches,
            List<Map<String, Object>> caseMatches,
            List<String> keywords,
            List<Map<String, Object>> evidenceSources,
            FileStorageService.StoredFile storedImage,
            Long modelProfileId,
            boolean reasoningEnabled,
            String chatMode,
            boolean retrievalUsed,
            String retrievalStatus,
            List<String> generatedImages,
            UserEntity user) {
        QaHistoryEntity qa = new QaHistoryEntity();
        qa.setConversationId(conversation.getId());
        qa.setQuestion(question);
        qa.setAnswer(llmAnswer.answer());
        qa.setRelatedEntitiesJson(toJson(graphMatches.stream().limit(12).toList()));
        qa.setGraphContextJson(toJson(graphMatches));
        qa.setDiseaseInfoMatchesJson(toJson(diseaseMatches.stream().limit(8).toList()));
        qa.setDiseaseCaseMatchesJson(toJson(caseMatches.stream().limit(8).toList()));
        qa.setKeywordsJson(toJson(keywords));
        qa.setUsedModel(llmAnswer.usedModel());
        qa.setProvider(llmAnswer.provider());
        qa.setModelProfileId(modelProfileId);
        qa.setChatMode(normalizeChatMode(chatMode));
        qa.setRetrievalUsed(retrievalUsed);
        qa.setRetrievalStatus(retrievalStatus == null || retrievalStatus.isBlank() ? RETRIEVAL_SUCCESS : retrievalStatus);
        qa.setReasoningEnabled(reasoningEnabled);
        qa.setReasoningContent(llmAnswer.reasoningContent());
        qa.setFallbackUsed(llmAnswer.fallbackUsed());
        qa.setEvidenceSourcesJson(toJson(evidenceSources));
        qa.setGeneratedImagesJson(toJson(generatedImages == null ? List.of() : generatedImages));
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
            conversation.setTitle(question.length() > 30 ? question.substring(0, 30) + "…" : question);
        }
        conversation.setUpdatedAt(LocalDateTime.now());
        return qa;
    }

    private List<Map<String, Object>> graphMatches(List<String> keywords) {
        List<Map<String, Object>> graphMatches = new ArrayList<>();
        try {
            for (String keyword : keywords) {
                graphMatches.addAll(graphService.search(keyword));
            }
        } catch (Exception ignored) {
            return new ArrayList<>();
        }
        return graphMatches;
    }

    private List<Map<String, Object>> diseaseMatches(String question, List<String> keywords, UserEntity user) {
        List<Map<String, Object>> diseaseMatches = new ArrayList<>(diseaseService.search(question, user));
        if (diseaseMatches.isEmpty()) {
            for (String keyword : keywords) diseaseMatches.addAll(diseaseService.search(keyword, user));
        }
        return diseaseMatches;
    }

    private List<Map<String, Object>> caseMatches(String question, List<String> keywords, UserEntity user) {
        List<Map<String, Object>> caseMatches = new ArrayList<>(medicalCaseService.search(question, user));
        if (caseMatches.isEmpty()) {
            for (String keyword : keywords) caseMatches.addAll(medicalCaseService.search(keyword, user));
        }
        return caseMatches;
    }

    private List<String> generateImagesForStream(
            String question,
            byte[] referenceImageBytes,
            String referenceImageContentType,
            LlmProfileService.RuntimeProfile profile,
            SseEmitter emitter) {
        sendEvent(emitter, "metadata", orderedMap("imageGenerationStatus", "running"));
        LlmService.ImageGenerationResult imageResult = llm.generateImage(question, referenceImageBytes, referenceImageContentType, profile);
        if (imageResult.imageUrls().isEmpty()) {
            sendEvent(emitter, "metadata", orderedMap("imageGenerationStatus", "failed"));
            sendEvent(emitter, "answer", orderedMap("chunk", "\n\n> 图片生成暂不可用，请稍后重试或检查百炼图片模型免费额度。\n"));
        } else {
            sendEvent(emitter, "metadata", orderedMap(
                    "imageGenerationStatus", "success",
                    "generatedImages", imageResult.imageUrls(),
                    "imageModel", imageResult.usedModel()));
            sendEvent(emitter, "answer", orderedMap("chunk", imageMarkdown(imageResult.imageUrls())));
        }
        return imageResult.imageUrls();
    }

    private LlmService.LlmAnswer appendGeneratedImages(LlmService.LlmAnswer answer, List<String> imageUrls, boolean imageFallbackUsed) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return imageFallbackUsed
                    ? new LlmService.LlmAnswer(answer.answer() + "\n\n> 图片生成暂不可用，请稍后重试或检查百炼图片模型免费额度。", answer.usedModel(), answer.provider(), true, answer.reasoningContent())
                    : answer;
        }
        return new LlmService.LlmAnswer(answer.answer() + imageMarkdown(imageUrls), answer.usedModel(), answer.provider(), answer.fallbackUsed(), answer.reasoningContent());
    }

    private String imageMarkdown(List<String> imageUrls) {
        StringBuilder builder = new StringBuilder("\n\n### 生成图片\n\n");
        for (int i = 0; i < imageUrls.size(); i++) {
            builder.append("![MedRisk 生成图片 ").append(i + 1).append("](").append(imageUrls.get(i)).append(")\n\n");
        }
        return builder.toString();
    }

    private void emitAnswerChunks(SseEmitter emitter, String answer) {
        String value = answer == null ? "" : answer;
        int chunkSize = 48;
        for (int i = 0; i < value.length(); i += chunkSize) {
            sendEvent(emitter, "answer", orderedMap("chunk", value.substring(i, Math.min(value.length(), i + chunkSize))));
        }
    }

    private void sendEvent(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (Exception ignored) {
            emitter.complete();
        }
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

    private String buildContext(
            List<Map<String, Object>> graph,
            List<Map<String, Object>> diseases,
            List<Map<String, Object>> cases,
            List<QaHistoryEntity> previousMessages) {
        StringBuilder context = new StringBuilder();
        if (!previousMessages.isEmpty()) {
            context.append("【最近对话】\n");
            previousMessages.stream()
                    .skip(Math.max(0, previousMessages.size() - 6))
                    .forEach(row -> context.append("- 用户：").append(truncate(row.getQuestion(), 160))
                            .append("\n  助手：").append(truncate(row.getAnswer(), 260)).append('\n'));
        }
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

    private List<Map<String, Object>> evidenceSources(List<Map<String, Object>> graph, List<Map<String, Object>> diseases, List<Map<String, Object>> cases) {
        List<Map<String, Object>> sources = new ArrayList<>();
        graph.stream().limit(8).forEach(row -> sources.add(orderedMap(
                "type", "知识图谱",
                "title", String.valueOf(row.getOrDefault("name", "图谱实体")),
                "summary", String.valueOf(row.getOrDefault("type", "Entity")))));
        diseases.stream().limit(5).forEach(row -> sources.add(orderedMap(
                "type", "疾病档案",
                "title", String.valueOf(row.getOrDefault("diseaseName", "疾病档案")),
                "summary", String.valueOf(row.getOrDefault("department", "")))));
        cases.stream().limit(4).forEach(row -> sources.add(orderedMap(
                "type", "病历案例",
                "title", String.valueOf(row.getOrDefault("caseTitle", "病历案例")),
                "summary", String.valueOf(row.getOrDefault("diagnosis", "")))));
        return sources.stream().limit(12).toList();
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

    private String truncate(String value, int maxLength) {
        String cleaned = clean(value).replaceAll("\\s+", " ");
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength) + "...";
    }

    private Map<String, Object> orderedMap(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put(String.valueOf(pairs[i]), pairs[i + 1]);
        return map;
    }

    private record StreamImage(byte[] bytes, String contentType, String filename) {
    }
}
