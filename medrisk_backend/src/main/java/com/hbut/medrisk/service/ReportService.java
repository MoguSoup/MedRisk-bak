package com.hbut.medrisk.service;

import com.hbut.medrisk.dto.ReportResponse;
import com.hbut.medrisk.entity.PredictionRecordEntity;
import com.hbut.medrisk.entity.ReportRecordEntity;
import com.hbut.medrisk.entity.UserEntity;
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
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {
    private final ReportRecordRepository reports;
    private final PredictionService predictionService;
    private final AuditService auditService;

    public ReportService(ReportRecordRepository reports, PredictionService predictionService, AuditService auditService) {
        this.reports = reports;
        this.predictionService = predictionService;
        this.auditService = auditService;
    }

    @Transactional
    public ReportResponse generate(Long predictionId, UserEntity user) {
        PredictionRecordEntity prediction = predictionService.requireAccessibleRecord(predictionId, user);
        ReportRecordEntity report = new ReportRecordEntity();
        report.setPredictionId(prediction.getId());
        report.setReportTitle("MedRisk AI " + prediction.getDiseaseName() + "风险评估报告");
        report.setReportHtml(renderHtml(prediction));
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

    private String renderHtml(PredictionRecordEntity prediction) {
        return """
                <article class="medrisk-report">
                  <h1>MedRisk AI 风险评估报告</h1>
                  <p><strong>病种：</strong>%s</p>
                  <p><strong>风险等级：</strong>%s</p>
                  <p><strong>风险概率：</strong>%.2f%%</p>
                  <p><strong>置信度：</strong>%.2f%%</p>
                  <p><strong>模型版本：</strong>%s</p>
                  <h2>模型输出</h2>
                  <pre>%s</pre>
                  <p class="disclaimer">本系统仅用于教学演示和健康风险提示，不能替代医生诊断。</p>
                </article>
                """.formatted(
                prediction.getDiseaseName(),
                labelCn(prediction.getRiskLabel()),
                prediction.getRiskProbability() * 100,
                prediction.getConfidence() * 100,
                prediction.getModelVersion(),
                escape(prediction.getResultJson()));
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
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String labelCn(String label) {
        return switch (label) {
            case "high" -> "高风险";
            case "medium" -> "中风险";
            default -> "低风险";
        };
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
