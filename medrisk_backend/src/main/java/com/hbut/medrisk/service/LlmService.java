package com.hbut.medrisk.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class LlmService {
    public record LlmAnswer(String answer, String usedModel, String provider, boolean fallbackUsed, String reasoningContent) {
    }

    public record ImageGenerationResult(List<String> imageUrls, String usedModel, boolean fallbackUsed) {
    }

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final String visionModel;
    private final String imageModel;
    private final String modelServiceUrl;
    private final boolean huggingFaceEnabled;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public LlmService(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${medrisk.model-service-url}") String modelServiceUrl,
            @Value("${medrisk.llm.base-url}") String baseUrl,
            @Value("${medrisk.llm.api-key}") String apiKey,
            @Value("${medrisk.llm.model}") String model,
            @Value("${medrisk.llm.vision-model}") String visionModel,
            @Value("${medrisk.llm.image-model:wanx-v1}") String imageModel,
            @Value("${medrisk.hf-llm.enabled:false}") boolean huggingFaceEnabled,
            @Value("${medrisk.http.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${medrisk.http.read-timeout-ms:20000}") int readTimeoutMs) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.modelServiceUrl = trimTrailingSlash(modelServiceUrl);
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null || model.isBlank() ? "qwen-plus" : model.trim();
        this.visionModel = visionModel == null || visionModel.isBlank() ? "qwen3.5-omni-plus-2026-03-15" : visionModel.trim();
        this.imageModel = imageModel == null || imageModel.isBlank() ? "wanx-v1" : imageModel.trim();
        this.huggingFaceEnabled = huggingFaceEnabled;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = Math.max(readTimeoutMs, 30000);
    }

    public boolean configured() {
        return !apiKey.isBlank();
    }

    public boolean visionConfigured() {
        return configured() && !visionModel.isBlank();
    }

    public String visionModelName(LlmProfileService.RuntimeProfile profile) {
        return chooseVisionModel(profile);
    }

    public String summarize(String title, String content) {
        if (!configured()) {
            return fallbackSummary(content);
        }
        String prompt = "请为以下医疗知识文档生成120字以内中文摘要，保留疾病、症状、治疗、科室等关键信息。\n标题："
                + title + "\n内容：\n" + safeLimit(content, 6000);
        return chatText(envProfile(), model, prompt, null, null, fallbackSummary(content), false).answer();
    }

    public String extractKnowledgeTriplets(String title, String chunk, LlmProfileService.RuntimeProfile profile) {
        if (!configured(profile)) {
            return "";
        }
        String prompt = """
                你是医学知识图谱三元组抽取器。请只从给定文本中抽取事实，不要编造。
                节点类型只能使用：Disease、Symptom、BodyPart、Pathogen、Drug、Treatment、Exam、Complication、RiskFactor、Department、Document、TimePoint。
                关系类型只能使用：DIAGNOSED_AS、CAUSED_BY、SHOWS_SYMPTOM、AFFECTS_BODYPART、PREVENTED_BY、TREATED_WITH、REQUIRES_EXAMINATION、OCCURS_AT、AGGRAVATED_BY、MANAGED_BY、RELATED_TO。
                请输出 JSON 数组，不要 Markdown，不要解释。每项格式：
                {"head":"","head_type":"Disease","relation":"SHOWS_SYMPTOM","relation_label":"表现症状","tail":"","tail_type":"Symptom","head_description":"","tail_description":""}
                标题：%s
                文本：
                %s
                """.formatted(title, safeLimit(chunk, 1200));
        return chatText(profile, profile.modelName(), prompt, null, null, "", false).answer();
    }

    public String answer(String question, String context, byte[] imageBytes, String imageContentType) {
        return answerDetailed(question, context, imageBytes, imageContentType).answer();
    }

    public LlmAnswer answerDetailed(String question, String context, byte[] imageBytes, String imageContentType) {
        return answerDetailed(question, context, imageBytes, imageContentType, envProfile(), false);
    }

    public LlmAnswer answerDetailed(
            String question,
            String context,
            byte[] imageBytes,
            String imageContentType,
            LlmProfileService.RuntimeProfile profile,
            boolean reasoningEnabled) {
        String disclaimer = "本回答仅用于教学演示和健康知识参考，不能替代医生诊断。";
        String fallback = fallbackAnswer(question, context, imageBytes != null, false, disclaimer);
        String prompt = qaPrompt(question, context, disclaimer);
        if (huggingFaceEnabled && imageBytes == null && !reasoningEnabled) {
            LlmAnswer localAnswer = chatHuggingFace(question, context, disclaimer);
            if (localAnswer != null) {
                return localAnswer;
            }
        }
        if (!configured(profile)) {
            return new LlmAnswer(fallback, fallbackModelName(profile, imageBytes != null), providerName(profile == null ? null : profile.baseUrl()), true, "");
        }
        if (imageBytes != null) {
            String autoVisionModel = chooseVisionModel(profile);
            return chatText(profile, autoVisionModel, prompt, imageBytes, imageContentType, fallbackAnswer(question, context, true, true, disclaimer), reasoningEnabled);
        }
        return chatText(profile, profile.modelName(), prompt, null, null, fallback, reasoningEnabled);
    }

    public ImageGenerationResult generateImage(String prompt, byte[] referenceImageBytes, String referenceImageContentType, LlmProfileService.RuntimeProfile profile) {
        if (!configured(profile)) {
            return new ImageGenerationResult(List.of(), imageModel, true);
        }
        try {
            String apiBaseUrl = dashScopeApiBase(profile.baseUrl());
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("prompt", safeLimit(prompt, 800));
            if (referenceImageBytes != null && referenceImageBytes.length > 0) {
                String mediaType = referenceImageContentType == null || referenceImageContentType.isBlank() ? "image/jpeg" : referenceImageContentType;
                input.put("ref_img", "data:" + mediaType + ";base64," + Base64.getEncoder().encodeToString(referenceImageBytes));
            }
            Map<String, Object> parameters = new LinkedHashMap<>();
            parameters.put("size", "1024*1024");
            parameters.put("n", 1);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", imageModel);
            body.put("input", input);
            body.put("parameters", parameters);

            HttpURLConnection create = (HttpURLConnection) URI.create(apiBaseUrl + "/services/aigc/text2image/image-synthesis").toURL().openConnection();
            create.setRequestMethod("POST");
            create.setConnectTimeout(connectTimeoutMs);
            create.setReadTimeout(readTimeoutMs);
            create.setDoOutput(true);
            create.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            create.setRequestProperty("Authorization", "Bearer " + profile.apiKey());
            create.setRequestProperty("X-DashScope-Async", "enable");
            try (OutputStream output = create.getOutputStream()) {
                output.write(objectMapper.writeValueAsBytes(body));
            }
            if (create.getResponseCode() < 200 || create.getResponseCode() >= 300) {
                create.disconnect();
                return new ImageGenerationResult(List.of(), imageModel, true);
            }
            JsonNode createRoot;
            try (BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(create.getInputStream(), StandardCharsets.UTF_8))) {
                createRoot = objectMapper.readTree(reader);
            } finally {
                create.disconnect();
            }
            String taskId = createRoot.path("output").path("task_id").asText("");
            if (taskId.isBlank()) {
                return new ImageGenerationResult(List.of(), imageModel, true);
            }
            for (int attempt = 0; attempt < 10; attempt++) {
                Thread.sleep(1500L);
                HttpURLConnection query = (HttpURLConnection) URI.create(apiBaseUrl + "/tasks/" + taskId).toURL().openConnection();
                query.setRequestMethod("GET");
                query.setConnectTimeout(connectTimeoutMs);
                query.setReadTimeout(readTimeoutMs);
                query.setRequestProperty("Authorization", "Bearer " + profile.apiKey());
                if (query.getResponseCode() < 200 || query.getResponseCode() >= 300) {
                    query.disconnect();
                    continue;
                }
                JsonNode root;
                try (BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(query.getInputStream(), StandardCharsets.UTF_8))) {
                    root = objectMapper.readTree(reader);
                } finally {
                    query.disconnect();
                }
                String status = root.path("output").path("task_status").asText("");
                if ("SUCCEEDED".equalsIgnoreCase(status)) {
                    List<String> urls = new ArrayList<>();
                    root.path("output").path("results").forEach(item -> {
                        String url = item.path("url").asText("");
                        if (!url.isBlank()) urls.add(url);
                    });
                    return new ImageGenerationResult(urls, imageModel, urls.isEmpty());
                }
                if ("FAILED".equalsIgnoreCase(status) || "CANCELED".equalsIgnoreCase(status)) {
                    return new ImageGenerationResult(List.of(), imageModel, true);
                }
            }
            return new ImageGenerationResult(List.of(), imageModel, true);
        } catch (Exception ex) {
            return new ImageGenerationResult(List.of(), imageModel, true);
        }
    }

    public LlmAnswer streamAnswer(
            String question,
            String context,
            LlmProfileService.RuntimeProfile profile,
            boolean reasoningEnabled,
            Consumer<String> reasoningConsumer,
            Consumer<String> answerConsumer) {
        return streamAnswer(question, context, profile, reasoningEnabled, null, null, reasoningConsumer, answerConsumer);
    }

    public LlmAnswer streamAnswer(
            String question,
            String context,
            LlmProfileService.RuntimeProfile profile,
            boolean reasoningEnabled,
            byte[] imageBytes,
            String imageContentType,
            Consumer<String> reasoningConsumer,
            Consumer<String> answerConsumer) {
        String disclaimer = "本回答仅用于教学演示和健康知识参考，不能替代医生诊断。";
        String fallback = fallbackAnswer(question, context, imageBytes != null, imageBytes != null && visionConfigured(), disclaimer);
        String prompt = qaPrompt(question, context, disclaimer);
        return streamChat(profile, prompt, fallback, reasoningEnabled, imageBytes, imageContentType, reasoningConsumer, answerConsumer);
    }

    public LlmAnswer streamDailyChat(
            String question,
            String conversationContext,
            LlmProfileService.RuntimeProfile profile,
            boolean reasoningEnabled,
            Consumer<String> reasoningConsumer,
            Consumer<String> answerConsumer) {
        String fallback = fallbackDailyAnswer(question);
        String prompt = dailyPrompt(question, conversationContext);
        return streamChat(profile, prompt, fallback, reasoningEnabled, null, null, reasoningConsumer, answerConsumer);
    }

    private LlmAnswer streamChat(
            LlmProfileService.RuntimeProfile profile,
            String prompt,
            String fallback,
            boolean reasoningEnabled,
            byte[] imageBytes,
            String imageContentType,
            Consumer<String> reasoningConsumer,
            Consumer<String> answerConsumer) {
        if (!configured(profile)) {
            emitText(fallback, answerConsumer);
            return new LlmAnswer(fallback, fallbackModelName(profile, imageBytes != null), providerName(profile == null ? null : profile.baseUrl()), true, "");
        }
        try {
            String usedModel = imageBytes == null ? profile.modelName() : chooseVisionModel(profile);
            Map<String, Object> body = chatBody(usedModel, prompt, imageBytes, imageContentType, reasoningEnabled && profile.reasoningSupported(), profile.reasoningProtocol());
            body.put("stream", true);
            HttpURLConnection connection = (HttpURLConnection) URI.create(profile.baseUrl() + "/chat/completions").toURL().openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(connectTimeoutMs);
            connection.setReadTimeout(readTimeoutMs);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "text/event-stream");
            connection.setRequestProperty("Authorization", "Bearer " + profile.apiKey());
            byte[] payload = objectMapper.writeValueAsBytes(body);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(payload);
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                emitText(fallback, answerConsumer);
                return new LlmAnswer(fallback, usedModel, providerName(profile.baseUrl()), true, "");
            }
            StringBuilder answer = new StringBuilder();
            StringBuilder reasoning = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.startsWith("data:")) continue;
                    String data = trimmed.substring(5).trim();
                    if (data.isBlank() || "[DONE]".equals(data)) break;
                    JsonNode delta = objectMapper.readTree(data).path("choices").path(0).path("delta");
                    String reasoningChunk = delta.path("reasoning_content").asText("");
                    if (!reasoningChunk.isBlank()) {
                        reasoning.append(reasoningChunk);
                        reasoningConsumer.accept(reasoningChunk);
                    }
                    String answerChunk = delta.path("content").asText("");
                    if (!answerChunk.isBlank()) {
                        answer.append(answerChunk);
                        answerConsumer.accept(answerChunk);
                    }
                }
            } finally {
                connection.disconnect();
            }
            if (answer.isEmpty()) {
                emitText(fallback, answerConsumer);
                return new LlmAnswer(fallback, usedModel, providerName(profile.baseUrl()), true, reasoning.toString());
            }
            return new LlmAnswer(answer.toString(), usedModel, providerName(profile.baseUrl()), false, reasoning.toString());
        } catch (Exception ex) {
            emitText(fallback, answerConsumer);
            return new LlmAnswer(fallback, fallbackModelName(profile, imageBytes != null), providerName(profile.baseUrl()), true, "");
        }
    }

    private String qaPrompt(String question, String context, String disclaimer) {
        return """
                你是 MedRisk 医疗平台的智能问答助手，只回答医疗健康、疾病风险预测、医学知识库、病历/文档、知识图谱、模型训练评估和平台使用相关问题。
                如果问题明显属于金融、编程、娱乐、政治闲聊、通用写作或其他平台外内容，必须拒答，并引导用户回到 MedRisk 医疗平台相关问题。
                请严格基于给定知识库上下文回答问题；如果上下文不足，请明确说明证据不足。
                回答必须使用中文、结构清晰、谨慎表达，并优先按“结论、依据、建议、注意事项”组织。
                每条关键结论尽量对应知识库上下文中的疾病档案、病历案例或图谱实体，不要编造未给出的检查结果。
                结尾必须保留免责声明。
                输出必须是标准 Markdown：标题使用 ## 或 ###，列表使用 -，强调使用 **文本**；不要输出裸露的 ***标题、装饰性星号或未闭合的 Markdown 标记。
                问题：%s
                知识库上下文：
                %s
                免责声明：%s
                """.formatted(question, safeLimit(context, 9000), disclaimer);
    }

    private String dailyPrompt(String question, String conversationContext) {
        return """
                你是 MedRisk 平台的日常聊天助手，可以进行普通寒暄、平台使用帮助、学习交流和轻量工作协助。
                当前模式是“日常聊天”，不要声称已经检索知识图谱、疾病档案或病历案例；如果用户提出医学知识、疾病风险、症状、检查、治疗、药物或诊疗建议，请提醒用户切换到“医学问答”模式以使用检索证据。
                不回答违法、危险、隐私窃取、绕过安全限制或替代医生诊断的请求。
                输出必须是标准 Markdown：标题使用 ## 或 ###，列表使用 -，强调使用 **文本**；不要输出裸露的 ***标题、装饰性星号或未闭合的 Markdown 标记。
                最近对话：
                %s
                用户问题：%s
                """.formatted(safeLimit(conversationContext, 4000), question);
    }

    private LlmAnswer chatHuggingFace(String question, String context, String disclaimer) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("question", question);
            body.put("context", safeLimit(context, 9000));
            body.put("disclaimer", disclaimer);
            String json = restTemplate.postForObject(modelServiceUrl + "/qa/generate", body, String.class);
            JsonNode root = objectMapper.readTree(json);
            String answer = root.path("answer").asText();
            if (answer.isBlank()) {
                return null;
            }
            String usedModel = root.path("usedModel").asText("huggingface-local");
            String provider = root.path("provider").asText("huggingface-local");
            return new LlmAnswer(answer, usedModel, provider, root.path("fallbackUsed").asBoolean(false), "");
        } catch (Exception ex) {
            return null;
        }
    }

    private LlmAnswer chatText(
            LlmProfileService.RuntimeProfile profile,
            String usedModel,
            String prompt,
            byte[] imageBytes,
            String imageContentType,
            String fallback,
            boolean reasoningEnabled) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(profile.apiKey());
            Map<String, Object> body = chatBody(usedModel, prompt, imageBytes, imageContentType, reasoningEnabled && profile.reasoningSupported(), profile.reasoningProtocol());
            String json = restTemplate.postForObject(profile.baseUrl() + "/chat/completions", new HttpEntity<>(body, headers), String.class);
            JsonNode root = objectMapper.readTree(json);
            JsonNode message = root.path("choices").path(0).path("message");
            String content = message.path("content").asText();
            String reasoning = message.path("reasoning_content").asText("");
            if (content.isBlank()) {
                return new LlmAnswer(fallback, usedModel, providerName(profile.baseUrl()), true, reasoning);
            }
            return new LlmAnswer(content, usedModel, providerName(profile.baseUrl()), false, reasoning);
        } catch (Exception ex) {
            return new LlmAnswer(fallback, usedModel, providerName(profile.baseUrl()), true, "");
        }
    }

    private Map<String, Object> chatBody(
            String usedModel,
            String prompt,
            byte[] imageBytes,
            String imageContentType,
            boolean reasoningEnabled,
            String reasoningProtocol) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", usedModel);
        body.put("temperature", 0.2);
        if (reasoningEnabled && ("bailian".equals(reasoningProtocol) || "openai".equals(reasoningProtocol))) {
            body.put("enable_thinking", true);
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content",
                "你是 MedRisk 医疗平台助手。请遵守用户消息中的模式要求。医学问答必须基于证据，日常聊天不得声称做过知识库检索。所有回答使用标准 Markdown，不输出裸露的装饰性星号，不替代医生诊断。"));
        if (imageBytes == null) {
            messages.add(Map.of("role", "user", "content", prompt));
        } else {
            String mediaType = imageContentType == null || imageContentType.isBlank() ? "image/jpeg" : imageContentType;
            String dataUrl = "data:" + mediaType + ";base64," + Base64.getEncoder().encodeToString(imageBytes);
            messages.add(Map.of(
                    "role", "user",
                    "content", List.of(
                            Map.of("type", "text", "text", prompt),
                            Map.of("type", "image_url", "image_url", Map.of("url", dataUrl)))));
        }
        body.put("messages", messages);
        return body;
    }

    private boolean configured(LlmProfileService.RuntimeProfile profile) {
        return profile != null && profile.apiKey() != null && !profile.apiKey().isBlank();
    }

    private LlmProfileService.RuntimeProfile envProfile() {
        return new LlmProfileService.RuntimeProfile(null, model, providerName(baseUrl), baseUrl, model, apiKey, false, "none");
    }

    private void emitText(String value, Consumer<String> consumer) {
        if (value == null || value.isBlank()) return;
        int chunkSize = 48;
        for (int i = 0; i < value.length(); i += chunkSize) {
            consumer.accept(value.substring(i, Math.min(value.length(), i + chunkSize)));
        }
    }

    private String fallbackModelName(LlmProfileService.RuntimeProfile profile, boolean imageProvided) {
        if (imageProvided) {
            return chooseVisionModel(profile);
        }
        if (profile != null && profile.modelName() != null && !profile.modelName().isBlank()) {
            return profile.modelName();
        }
        return model == null || model.isBlank() ? "local-fallback" : model;
    }

    private String chooseVisionModel(LlmProfileService.RuntimeProfile profile) {
        String selected = profile == null ? "" : safe(profile.modelName()).toLowerCase();
        if (selected.contains("omni") || selected.contains("vl") || selected.contains("vision")) {
            return profile.modelName();
        }
        return visionModel;
    }

    private String providerName(String source) {
        String value = source == null ? "" : source.toLowerCase();
        if (value.contains("dashscope") || value.contains("aliyun") || value.contains("maas.aliyuncs")) return "dashscope";
        if (value.contains("deepseek")) return "deepseek";
        if (value.contains("openai")) return "openai-compatible";
        if (value.contains("huatuo")) return "huatuogpt-compatible";
        return source == null || source.isBlank() ? (configured() ? "openai-compatible" : "local-fallback") : "openai-compatible";
    }

    private String fallbackSummary(String content) {
        String text = safeLimit(content == null ? "" : content.replaceAll("\\s+", " ").trim(), 180);
        return text.isBlank() ? "暂无可提取的文档摘要。" : text;
    }

    private String fallbackAnswer(String question, String context, boolean hasImage, boolean imageUsed, String disclaimer) {
        StringBuilder answer = new StringBuilder();
        answer.append("已根据当前知识库检索结果回答：").append('\n');
        if (context == null || context.isBlank()) {
            answer.append("暂未检索到足够的图谱、疾病档案或病历案例信息。建议补充相关文档后重新构建知识图谱。");
        } else {
            answer.append(safeLimit(context.replaceAll("\\s+", " ").trim(), 900));
        }
        if (hasImage && !imageUsed) {
            answer.append("\n\n图片已保存，但当前未配置视觉模型，本次回答未对图片内容做识别。");
        }
        answer.append("\n\n问题：").append(question);
        answer.append("\n").append(disclaimer);
        return answer.toString();
    }

    private String fallbackDailyAnswer(String question) {
        return """
                ## 已收到

                我是 MedRisk AI，可以和你进行日常交流，也可以帮助你了解平台功能。

                - 如果你想咨询疾病、症状、检查、治疗、药物或风险预测，请切换到 **医学问答** 模式。
                - 如果你只是想了解系统怎么用，可以继续在 **日常聊天** 模式提问。

                你的问题：%s
                """.formatted(question);
    }

    private String safeLimit(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "……";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String dashScopeApiBase(String baseUrl) {
        String trimmed = trimTrailingSlash(baseUrl);
        if (trimmed.endsWith("/compatible-mode/v1")) {
            return trimmed.substring(0, trimmed.length() - "/compatible-mode/v1".length()) + "/api/v1";
        }
        if (trimmed.endsWith("/v1")) {
            return trimmed.substring(0, trimmed.length() - 3) + "/api/v1";
        }
        return trimmed + "/api/v1";
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "https://dashscope.aliyuncs.com/compatible-mode/v1";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
