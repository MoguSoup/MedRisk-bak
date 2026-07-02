package com.hbut.medrisk.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbut.medrisk.dto.ReportGenerateRequest;
import com.hbut.medrisk.dto.ReportResponse;
import com.hbut.medrisk.entity.ConversationEntity;
import com.hbut.medrisk.entity.PredictionRecordEntity;
import com.hbut.medrisk.entity.QaHistoryEntity;
import com.hbut.medrisk.entity.ReportRecordEntity;
import com.hbut.medrisk.entity.UserEntity;
import com.hbut.medrisk.repository.ConversationRepository;
import com.hbut.medrisk.repository.QaHistoryRepository;
import com.hbut.medrisk.repository.ReportRecordRepository;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfWriter;
import jakarta.persistence.EntityNotFoundException;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {
    private final ReportRecordRepository reports;
    private final PredictionService predictionService;
    private final AuditService auditService;
    private final ConversationRepository conversations;
    private final QaHistoryRepository qaHistory;
    private final ObjectMapper objectMapper;

    public ReportService(
            ReportRecordRepository reports,
            PredictionService predictionService,
            AuditService auditService,
            ConversationRepository conversations,
            QaHistoryRepository qaHistory,
            ObjectMapper objectMapper) {
        this.reports = reports;
        this.predictionService = predictionService;
        this.auditService = auditService;
        this.conversations = conversations;
        this.qaHistory = qaHistory;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ReportResponse generate(Long predictionId, UserEntity user) {
        return generate(predictionId, null, user);
    }

    @Transactional
    public ReportResponse generate(Long predictionId, ReportGenerateRequest request, UserEntity user) {
        PredictionRecordEntity prediction = predictionService.requireAccessibleRecord(predictionId, user);
        List<QaHistoryEntity> selectedQa = selectedQa(request, user);
        boolean includeReasoning = request != null && Boolean.TRUE.equals(request.includeReasoning());
        ReportRecordEntity report = new ReportRecordEntity();
        report.setPredictionId(prediction.getId());
        report.setReportTitle("MedRisk AI " + prediction.getDiseaseName() + "风险评估报告");
        report.setReportHtml(renderHtml(prediction, selectedQa, includeReasoning));
        report.setGeneratedBy(user.getId());
        report.setCreatedAt(LocalDateTime.now());
        reports.save(report);
        auditService.log(user.getId(), "GENERATE_REPORT", "REPORT", report.getId().toString(), "{}");
        return toResponse(report);
    }

    public ReportResponse get(Long reportId, UserEntity user) {
        ReportRecordEntity report = requireAccessibleReport(reportId, user);
        return toResponse(report);
    }

    public List<ReportResponse> list(UserEntity user) {
        List<ReportRecordEntity> rows = "PATIENT".equals(user.getRole())
                ? reports.findTop100ByGeneratedByOrderByCreatedAtDesc(user.getId())
                : reports.findTop100ByOrderByCreatedAtDesc();
        return rows.stream().map(this::toResponse).toList();
    }

    public byte[] downloadPdf(Long reportId, UserEntity user) {
        ReportRecordEntity report = requireAccessibleReport(reportId, user);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, output);
            document.open();
            Font titleFont = chineseFont(18);
            Font bodyFont = chineseFont(11);
            document.add(new Paragraph(report.getReportTitle(), titleFont));
            document.add(new Paragraph("生成时间：" + report.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")), bodyFont));
            document.add(new Paragraph("报告编号：" + report.getId(), bodyFont));
            document.add(new Paragraph(" ", bodyFont));
            document.add(new Paragraph(stripHtml(report.getReportHtml()), bodyFont));
            document.close();
            auditService.log(user.getId(), "DOWNLOAD_REPORT", "REPORT", report.getId().toString(), "{}");
            return output.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("PDF 报告生成失败");
        }
    }

    private ReportRecordEntity requireAccessibleReport(Long reportId, UserEntity user) {
        ReportRecordEntity report = reports.findById(reportId)
                .orElseThrow(() -> new EntityNotFoundException("报告不存在"));
        if ("PATIENT".equals(user.getRole()) && !report.getGeneratedBy().equals(user.getId())) {
            throw new SecurityException("不能访问其他用户的报告");
        }
        return report;
    }

    private ReportResponse toResponse(ReportRecordEntity report) {
        return new ReportResponse(report.getId(), report.getPredictionId(), report.getReportTitle(), report.getReportHtml(), report.getCreatedAt());
    }

    private String renderHtml(PredictionRecordEntity prediction, List<QaHistoryEntity> selectedQa, boolean includeReasoning) {
        return """
                <article class="medrisk-report">
                  <h1>MedRisk AI 风险评估报告</h1>
                  <p><strong>病种：</strong>%s</p>
                  <p><strong>风险等级：</strong>%s</p>
                  <p><strong>风险概率：</strong>%.2f%%</p>
                  <p><strong>置信度：</strong>%.2f%%</p>
                  <p><strong>模型版本：</strong>%s</p>
                  <p><strong>模型来源说明：</strong>%s</p>
                  <h2>模型输出</h2>
                  <pre>%s</pre>
                  %s
                  <p class="disclaimer">本系统仅用于教学演示和健康风险提示，不能替代医生诊断。</p>
                </article>
                """.formatted(
                prediction.getDiseaseName(),
                labelCn(prediction.getRiskLabel()),
                prediction.getRiskProbability() * 100,
                prediction.getConfidence() * 100,
                prediction.getModelVersion(),
                modelSourceText(prediction.getModelVersion()),
                escape(prediction.getResultJson()),
                renderConsultationSummary(selectedQa, includeReasoning));
    }

    private List<QaHistoryEntity> selectedQa(ReportGenerateRequest request, UserEntity user) {
        if (request == null || request.conversationId() == null) {
            return List.of();
        }
        ConversationEntity conversation = conversations.findById(request.conversationId())
                .orElseThrow(() -> new EntityNotFoundException("问诊会话不存在"));
        if (!"ADMIN".equals(user.getRole()) && !conversation.getUserId().equals(user.getId())) {
            throw new SecurityException("不能同步其他用户的问诊内容");
        }
        List<QaHistoryEntity> rows = qaHistory.findByConversationIdOrderByCreatedAtAsc(conversation.getId());
        if (request.qaMessageIds() == null || request.qaMessageIds().isEmpty()) {
            return rows;
        }
        Set<Long> ids = new LinkedHashSet<>(request.qaMessageIds());
        return rows.stream().filter((item) -> ids.contains(item.getId())).toList();
    }

    private String renderConsultationSummary(List<QaHistoryEntity> rows, boolean includeReasoning) {
        if (rows == null || rows.isEmpty()) {
            return "";
        }
        String topic = joinDistinct(rows.stream().map(QaHistoryEntity::getQuestion).toList(), 280);
        String keywords = joinDistinct(rows.stream().flatMap((item) -> readStringList(item.getKeywordsJson()).stream()).toList(), 220);
        String evidence = joinDistinct(rows.stream().flatMap((item) -> evidenceTitles(item.getEvidenceSourcesJson()).stream()).toList(), 260);
        String answer = joinDistinct(rows.stream().map(QaHistoryEntity::getAnswer).toList(), 520);
        String clinical = joinDistinct(rows.stream().map((item) -> pickClinicalSentences(item.getAnswer())).toList(), 260);
        String advice = joinDistinct(rows.stream().map((item) -> pickAdviceSentences(item.getAnswer())).toList(), 260);
        String reasoning = includeReasoning
                ? joinDistinct(rows.stream().map(QaHistoryEntity::getReasoningContent).toList(), 420)
                : "无";
        String models = joinDistinct(rows.stream()
                .map((item) -> "%s · %s · %s".formatted(
                        blankToNone(item.getProvider()),
                        blankToNone(item.getUsedModel()),
                        item.getCreatedAt() == null ? "无时间" : item.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))))
                .toList(), 260);
        return """
                  <h2>问诊同步摘要</h2>
                  <table class="consultation-summary">
                    <tbody>
                      <tr><th>咨询主题</th><td>%s</td></tr>
                      <tr><th>关键症状/风险因素</th><td>%s</td></tr>
                      <tr><th>相关检查/治疗/药物</th><td>%s</td></tr>
                      <tr><th>知识图谱/疾病档案/病历证据</th><td>%s</td></tr>
                      <tr><th>模型答复要点</th><td>%s</td></tr>
                      <tr><th>推理过程/依据</th><td>%s</td></tr>
                      <tr><th>后续建议</th><td>%s</td></tr>
                      <tr><th>使用模型与时间</th><td>%s</td></tr>
                    </tbody>
                  </table>
                """.formatted(
                escape(blankToNone(topic)),
                escape(blankToNone(keywords)),
                escape(blankToNone(clinical)),
                escape(blankToNone(evidence)),
                escape(blankToNone(answer)),
                escape(blankToNone(reasoning)),
                escape(blankToNone(advice)),
                escape(blankToNone(models)));
    }

    private List<String> evidenceTitles(String json) {
        return readMapList(json).stream()
                .map((item) -> "%s · %s".formatted(item.getOrDefault("type", "证据"), item.getOrDefault("title", "未命名证据")))
                .toList();
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<Map<String, Object>> readMapList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String pickClinicalSentences(String answer) {
        return pickSentences(answer, List.of("症状", "检查", "检验", "治疗", "药", "用药", "风险因素", "血糖", "血压", "胆固醇"));
    }

    private String pickAdviceSentences(String answer) {
        return pickSentences(answer, List.of("建议", "注意", "随访", "复诊", "就医", "咨询医生", "生活方式", "监测"));
    }

    private String pickSentences(String value, List<String> keywords) {
        if (value == null || value.isBlank()) return "";
        String[] sentences = value.replace("\r", "\n").split("[。！？!?\n]");
        List<String> matched = new ArrayList<>();
        for (String sentence : sentences) {
            String trimmed = sentence.trim();
            if (trimmed.isEmpty()) continue;
            if (keywords.stream().anyMatch(trimmed::contains)) {
                matched.add(trimmed);
            }
            if (matched.size() >= 4) break;
        }
        return String.join("；", matched);
    }

    private String joinDistinct(List<String> values, int limit) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) continue;
            String normalized = value.replaceAll("\\s+", " ").trim();
            if (!normalized.isBlank()) result.add(normalized);
        }
        String joined = String.join("；", result);
        if (joined.length() <= limit) return joined;
        return joined.substring(0, Math.max(0, limit - 1)) + "…";
    }

    private String blankToNone(String value) {
        return value == null || value.isBlank() ? "无" : value;
    }

    private String stripHtml(String html) {
        return html.replaceAll("<[^>]+>", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String labelCn(String label) {
        return switch (label) {
            case "high" -> "高风险";
            case "medium" -> "中风险";
            default -> "低风险";
        };
    }

    private String modelSourceText(String modelVersion) {
        String value = modelVersion == null ? "" : modelVersion.toLowerCase();
        if (value.contains("logistic")) return "本次预测由 Logistic Regression 可解释基线模型生成，结果仅用于教学演示和健康风险提示。";
        if (value.contains("random_forest")) return "本次预测由 Random Forest 随机森林模型生成，结果仅用于教学演示和健康风险提示。";
        if (value.contains("extra_trees")) return "本次预测由 ExtraTrees 极端随机树模型生成，结果仅用于教学演示和健康风险提示。";
        if (value.contains("hist_gradient")) return "本次预测由 HistGradientBoosting 直方图提升树模型生成，结果仅用于教学演示和健康风险提示。";
        if (value.contains("lightgbm")) return "本次预测由 LightGBM 梯度提升树模型生成，结果仅用于教学演示和健康风险提示。";
        if (value.contains("catboost")) return "本次预测由 CatBoost 类别特征提升树模型生成，结果仅用于教学演示和健康风险提示。";
        if (value.contains("tabpfn")) return "本次预测由 TabPFN 表格基础模型生成，结果仅用于教学演示和健康风险提示。";
        if (value.contains("tabicl")) return "本次预测由 TabICL 表格上下文学习模型生成，结果仅用于教学演示和健康风险提示。";
        if (value.contains("fallback")) return "本次预测由后端教学兜底模型生成，模型服务不可用时用于保持演示流程。";
        return "本次预测由 XGBoost/结构化风险基线模型生成，结果仅用于教学演示和健康风险提示。";
    }

    private Font chineseFont(int size) throws Exception {
        try {
            BaseFont baseFont = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
            return new Font(baseFont, size);
        } catch (Exception ex) {
            return new Font(Font.HELVETICA, size);
        }
    }
}
