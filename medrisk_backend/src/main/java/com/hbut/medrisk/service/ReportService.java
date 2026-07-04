package com.hbut.medrisk.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbut.medrisk.dto.ModelPredictionResponse;
import com.hbut.medrisk.dto.PredictionFactorInfo;
import com.hbut.medrisk.dto.ReportGenerateRequest;
import com.hbut.medrisk.dto.ReportResponse;
import com.hbut.medrisk.entity.ConversationEntity;
import com.hbut.medrisk.entity.PredictionRecordEntity;
import com.hbut.medrisk.entity.QaHistoryEntity;
import com.hbut.medrisk.entity.ReportRecordEntity;
import com.hbut.medrisk.entity.UserEntity;
import com.hbut.medrisk.repository.ConversationRepository;
import com.hbut.medrisk.repository.PredictionRecordRepository;
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
    private final PredictionRecordRepository predictionRecords;
    private final ObjectMapper objectMapper;

    public ReportService(
            ReportRecordRepository reports,
            PredictionService predictionService,
            AuditService auditService,
            ConversationRepository conversations,
            QaHistoryRepository qaHistory,
            PredictionRecordRepository predictionRecords,
            ObjectMapper objectMapper) {
        this.reports = reports;
        this.predictionService = predictionService;
        this.auditService = auditService;
        this.conversations = conversations;
        this.qaHistory = qaHistory;
        this.predictionRecords = predictionRecords;
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
            for (String line : renderPdfLines(report, user)) {
                document.add(new Paragraph(line.isBlank() ? " " : line, bodyFont));
            }
            document.close();
            auditService.log(user.getId(), "DOWNLOAD_REPORT", "REPORT", report.getId().toString(), "{}");
            return output.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("PDF 报告生成失败");
        }
    }

    @Transactional
    public Map<String, Object> delete(Long reportId, UserEntity user) {
        ReportRecordEntity report = requireAccessibleReport(reportId, user);
        reports.delete(report);
        auditService.log(user.getId(), "DELETE_REPORT", "REPORT", reportId.toString(), "{}");
        return Map.of("deleted", true, "id", reportId);
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
        return new ReportResponse(report.getId(), report.getPredictionId(), report.getReportTitle(), displayHtml(report), report.getCreatedAt());
    }

    private String renderHtml(PredictionRecordEntity prediction, List<QaHistoryEntity> selectedQa, boolean includeReasoning) {
        ModelPredictionResponse model = readPredictionModel(prediction);
        String diseaseName = blankToDefault(model.diseaseName(), prediction.getDiseaseName());
        String riskLabel = blankToDefault(model.riskLabel(), prediction.getRiskLabel());
        String modelVersion = blankToDefault(model.modelVersion(), prediction.getModelVersion());
        String disclaimer = blankToDefault(model.disclaimer(), "本结果仅用于教学演示和健康风险提示，不能替代医生诊断。");
        return """
                <article class="medrisk-report">
                  <h1>MedRisk AI 风险评估报告</h1>
                  <section class="report-section">
                    <h2>风险结论</h2>
                    <dl class="report-summary">
                      <dt>患者</dt><dd>%s</dd>
                      <dt>病种</dt><dd>%s</dd>
                      <dt>风险等级</dt><dd>%s</dd>
                      <dt>风险概率</dt><dd>%.2f%%</dd>
                      <dt>置信度</dt><dd>%.2f%%</dd>
                      <dt>预测时间</dt><dd>%s</dd>
                    </dl>
                    <p class="report-conclusion">本次结构化风险预测提示该用户的%s风险为<strong>%s</strong>，风险概率约为<strong>%.2f%%</strong>。请结合病史、体格检查和线下检验结果，由专业医生综合判断。</p>
                  </section>
                  <section class="report-section">
                    <h2>关键风险因素</h2>
                    <table class="risk-factor-table">
                      <thead>
                        <tr><th>因素</th><th>输入值</th><th>影响程度</th><th>方向</th></tr>
                      </thead>
                      <tbody>
                        %s
                      </tbody>
                    </table>
                  </section>
                  <section class="report-section">
                    <h2>后续建议</h2>
                    <ul class="report-recommendations">
                      %s
                    </ul>
                  </section>
                  <section class="report-section">
                    <h2>模型信息</h2>
                    <dl class="report-summary">
                      <dt>模型版本</dt><dd>%s</dd>
                      <dt>模型来源说明</dt><dd>%s</dd>
                    </dl>
                  </section>
                  %s
                  <p class="disclaimer">%s</p>
                </article>
                """.formatted(
                escape(blankToDefault(prediction.getPatientName(), "演示患者")),
                escape(diseaseName),
                labelCn(riskLabel),
                model.riskProbability() * 100,
                model.confidence() * 100,
                prediction.getCreatedAt() == null ? "无时间" : prediction.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                escape(diseaseName),
                labelCn(riskLabel),
                model.riskProbability() * 100,
                renderRiskFactorRows(model.topFactors()),
                renderRecommendationItems(model.recommendations()),
                escape(modelVersion),
                escape(modelSourceText(modelVersion)),
                renderConsultationSummary(selectedQa, includeReasoning),
                escape(disclaimer));
    }

    private String displayHtml(ReportRecordEntity report) {
        String html = report.getReportHtml();
        if (usesLegacyRawModelOutput(html)) {
            return predictionRecords.findById(report.getPredictionId())
                    .map((prediction) -> renderHtml(prediction, List.of(), false))
                    .orElse(html);
        }
        return html;
    }

    private boolean usesLegacyRawModelOutput(String html) {
        return html != null && html.contains("<h2>模型输出</h2>") && html.contains("<pre>");
    }

    private ModelPredictionResponse readPredictionModel(PredictionRecordEntity prediction) {
        try {
            ModelPredictionResponse model = objectMapper.readValue(prediction.getResultJson(), ModelPredictionResponse.class);
            return new ModelPredictionResponse(
                    blankToDefault(model.diseaseType(), prediction.getDiseaseType()),
                    blankToDefault(model.diseaseName(), prediction.getDiseaseName()),
                    blankToDefault(model.riskLabel(), prediction.getRiskLabel()),
                    model.riskProbability(),
                    model.confidence(),
                    blankToDefault(model.modelVersion(), prediction.getModelVersion()),
                    model.topFactors() == null ? List.of() : model.topFactors(),
                    model.recommendations() == null ? List.of() : model.recommendations(),
                    blankToDefault(model.disclaimer(), "本结果仅用于教学演示和健康风险提示，不能替代医生诊断。"));
        } catch (Exception ex) {
            return new ModelPredictionResponse(
                    prediction.getDiseaseType(),
                    prediction.getDiseaseName(),
                    prediction.getRiskLabel(),
                    prediction.getRiskProbability(),
                    prediction.getConfidence(),
                    prediction.getModelVersion(),
                    List.of(),
                    List.of("请结合病史与线下检查综合判断。"),
                    "本结果仅用于教学演示和健康风险提示，不能替代医生诊断。");
        }
    }

    private String renderRiskFactorRows(List<PredictionFactorInfo> factors) {
        if (factors == null || factors.isEmpty()) {
            return "<tr><td colspan=\"4\">暂无关键因素明细，请结合原始检查数据和医生意见综合判断。</td></tr>";
        }
        return factors.stream()
                .map((factor) -> """
                        <tr>
                          <td>%s</td>
                          <td>%s</td>
                          <td>%s</td>
                          <td>%s</td>
                        </tr>
                        """.formatted(
                        escape(blankToDefault(factor.label(), factor.name())),
                        escape(formatFactorValue(factor.value())),
                        formatPercent(factor.impact()),
                        escape(directionCn(factor.direction()))))
                .reduce("", String::concat);
    }

    private String renderRecommendationItems(List<String> recommendations) {
        if (recommendations == null || recommendations.isEmpty()) {
            return "<li>请结合病史、体征和线下检查结果，由医生进一步评估。</li>";
        }
        return recommendations.stream()
                .filter((item) -> item != null && !item.isBlank())
                .map((item) -> "<li>" + escape(item) + "</li>")
                .reduce("", String::concat);
    }

    private List<String> renderPdfLines(ReportRecordEntity report, UserEntity user) {
        try {
            PredictionRecordEntity prediction = predictionService.requireAccessibleRecord(report.getPredictionId(), user);
            ModelPredictionResponse model = readPredictionModel(prediction);
            List<String> lines = new ArrayList<>();
            lines.add("风险结论");
            lines.add("患者：" + blankToDefault(prediction.getPatientName(), "演示患者"));
            lines.add("病种：" + blankToDefault(model.diseaseName(), prediction.getDiseaseName()));
            lines.add("风险等级：" + labelCn(blankToDefault(model.riskLabel(), prediction.getRiskLabel())));
            lines.add("风险概率：" + formatPercent(model.riskProbability()));
            lines.add("置信度：" + formatPercent(model.confidence()));
            lines.add("预测时间：" + (prediction.getCreatedAt() == null ? "无时间" : prediction.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))));
            lines.add("");
            lines.add("关键风险因素");
            if (model.topFactors() == null || model.topFactors().isEmpty()) {
                lines.add("暂无关键因素明细，请结合原始检查数据和医生意见综合判断。");
            } else {
                for (PredictionFactorInfo factor : model.topFactors()) {
                    lines.add("%s：输入值 %s，影响程度 %s，方向 %s".formatted(
                            blankToDefault(factor.label(), factor.name()),
                            formatFactorValue(factor.value()),
                            formatPercent(factor.impact()),
                            directionCn(factor.direction())));
                }
            }
            lines.add("");
            lines.add("后续建议");
            List<String> recommendations = model.recommendations() == null ? List.of() : model.recommendations();
            if (recommendations.isEmpty()) {
                lines.add("请结合病史、体征和线下检查结果，由医生进一步评估。");
            } else {
                recommendations.forEach((item) -> lines.add("• " + item));
            }
            lines.add("");
            lines.add("模型信息");
            String modelVersion = blankToDefault(model.modelVersion(), prediction.getModelVersion());
            lines.add("模型版本：" + modelVersion);
            lines.add("模型来源说明：" + modelSourceText(modelVersion));
            lines.addAll(consultationPdfLines(report));
            lines.add("");
            lines.add(blankToDefault(model.disclaimer(), "本结果仅用于教学演示和健康风险提示，不能替代医生诊断。"));
            return lines;
        } catch (Exception ignored) {
            return htmlToTextLines(displayHtml(report));
        }
    }

    private List<String> consultationPdfLines(ReportRecordEntity report) {
        String html = displayHtml(report);
        if (html == null || !html.contains("问诊同步摘要")) {
            return List.of();
        }
        int start = html.indexOf("<h2>问诊同步摘要</h2>");
        if (start < 0) {
            start = html.indexOf("问诊同步摘要");
        }
        int end = html.indexOf("</table>", start);
        String section = end > start ? html.substring(start, end + "</table>".length()) : html.substring(start);
        List<String> lines = htmlToTextLines(section);
        if (lines.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        result.add("");
        result.addAll(lines);
        return result;
    }

    private List<String> htmlToTextLines(String html) {
        String text = html == null ? "" : html
                .replaceAll("(?i)</h[1-6]>", "\n")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n")
                .replaceAll("(?i)</li>", "\n")
                .replaceAll("(?i)</tr>", "\n")
                .replaceAll("<[^>]+>", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&nbsp;", " ");
        List<String> lines = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String normalized = line.replaceAll("\\s+", " ").trim();
            if (!normalized.isBlank()) lines.add(normalized);
        }
        return lines.isEmpty() ? List.of("暂无报告内容") : lines;
    }

    private String formatFactorValue(Object value) {
        if (value == null) return "无";
        if (value instanceof Boolean bool) return bool ? "是" : "否";
        return String.valueOf(value);
    }

    private String formatPercent(double value) {
        return "%.2f%%".formatted(value * 100);
    }

    private String directionCn(String direction) {
        if ("increase".equals(direction)) return "升高风险";
        if ("decrease".equals(direction)) return "降低风险";
        return "中性/参考";
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
        String answer = joinDistinct(rows.stream().map(QaHistoryEntity::getAnswer).toList(), 1800);
        String clinical = joinDistinct(rows.stream().map((item) -> pickClinicalSentences(item.getAnswer())).toList(), 500);
        String advice = joinDistinct(rows.stream().map((item) -> pickAdviceSentences(item.getAnswer())).toList(), 500);
        String reasoning = includeReasoning
                ? joinDistinct(rows.stream().map(QaHistoryEntity::getReasoningContent).toList(), 1200)
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

    private String blankToDefault(String value, String fallback) {
        if (value != null && !value.isBlank()) return value;
        return fallback == null || fallback.isBlank() ? "无" : fallback;
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
