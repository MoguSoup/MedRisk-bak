package com.hbut.medrisk.controller;

import com.hbut.medrisk.dto.ApiResponse;
import com.hbut.medrisk.dto.ReportGenerateRequest;
import com.hbut.medrisk.dto.ReportResponse;
import com.hbut.medrisk.entity.UserEntity;
import com.hbut.medrisk.service.AuthService;
import com.hbut.medrisk.service.ReportService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
public class ReportController {
    private final AuthService authService;
    private final ReportService reportService;

    public ReportController(AuthService authService, ReportService reportService) {
        this.authService = authService;
        this.reportService = reportService;
    }

    @GetMapping
    ApiResponse<List<ReportResponse>> list(@RequestHeader(value = "Authorization", required = false) String authorization) {
        UserEntity user = authService.requireUser(authorization);
        return ApiResponse.ok(reportService.list(user));
    }

    @PostMapping("/generate/{predictionId}")
    ApiResponse<ReportResponse> generate(
            @PathVariable Long predictionId,
            @RequestBody(required = false) ReportGenerateRequest request,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        UserEntity user = authService.requireUser(authorization);
        return ApiResponse.ok(reportService.generate(predictionId, request, user));
    }

    @GetMapping("/{reportId}")
    ApiResponse<ReportResponse> get(
            @PathVariable Long reportId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        UserEntity user = authService.requireUser(authorization);
        return ApiResponse.ok(reportService.get(reportId, user));
    }

    @GetMapping("/{reportId}/download")
    ResponseEntity<byte[]> download(
            @PathVariable Long reportId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        UserEntity user = authService.requireUser(authorization);
        byte[] pdf = reportService.downloadPdf(reportId, user);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("MedRisk-AI-report-" + reportId + ".pdf", StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(pdf);
    }

    @DeleteMapping("/{reportId}")
    ApiResponse<Map<String, Object>> delete(
            @PathVariable Long reportId,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        UserEntity user = authService.requireUser(authorization);
        return ApiResponse.ok(reportService.delete(reportId, user));
    }
}
