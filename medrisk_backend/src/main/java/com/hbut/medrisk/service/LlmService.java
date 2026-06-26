package com.hbut.medrisk.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class LlmService {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final String visionModel;

    public LlmService(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${medrisk.llm.base-url}") String baseUrl,
            @Value("${medrisk.llm.api-key}") String apiKey,
            @Value("${medrisk.llm.model}") String model,
            @Value("${medrisk.llm.vision-model}") String visionModel) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
        this.visionModel = visionModel == null ? "" : visionModel.trim();
    }

    public boolean configured() {
        return !apiKey.isBlank();
    }

    public boolean visionConfigured() {
        return configured() && !visionModel.isBlank();
    }

    public String summarize(String title, String content) {
        if (!configured()) {
            return fallbackSummary(content);
        }
        String prompt = "请为以下医疗知识文档生成120字以内中文摘要，保留疾病、症状、治疗、科室等关键信息。\n标题："
                + title + "\n内容：\n" + safeLimit(content, 6000);
        return chatText(model, prompt, null, null, fallbackSummary(content));
    }

    public String answer(String question, String context, byte[] imageBytes, String imageContentType) {
        String disclaimer = "本回答仅用于教学演示和健康知识参考，不能替代医生诊断。";
        if (!configured()) {
            return fallbackAnswer(question, context, imageBytes != null, false, disclaimer);
        }
        String prompt = """
                你是医疗健康知识问答助手。请基于给定知识库上下文回答问题；如果上下文不足，请明确说明。
                回答必须使用中文、结构清晰、谨慎表达，并在结尾保留免责声明。
                问题：%s
                知识库上下文：
                %s
                免责声明：%s
                """.formatted(question, safeLimit(context, 9000), disclaimer);
        if (imageBytes != null && visionConfigured()) {
            return chatText(visionModel, prompt, imageBytes, imageContentType, fallbackAnswer(question, context, true, true, disclaimer));
        }
        return chatText(model, prompt, null, null, fallbackAnswer(question, context, imageBytes != null, false, disclaimer));
    }

    private String chatText(String usedModel, String prompt, byte[] imageBytes, String imageContentType, String fallback) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", usedModel);
            body.put("temperature", 0.2);
            List<Map<String, Object>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", "请用可靠、克制、中文医疗科普风格回答。"));
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
            String json = restTemplate.postForObject(baseUrl + "/chat/completions", new HttpEntity<>(body, headers), String.class);
            JsonNode root = objectMapper.readTree(json);
            String content = root.path("choices").path(0).path("message").path("content").asText();
            return content.isBlank() ? fallback : content;
        } catch (Exception ex) {
            return fallback;
        }
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

    private String safeLimit(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "……";
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "https://dashscope.aliyuncs.com/compatible-mode/v1";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
