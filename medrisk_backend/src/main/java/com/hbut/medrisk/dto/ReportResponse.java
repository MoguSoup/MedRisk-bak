package com.hbut.medrisk.dto;

import java.time.LocalDateTime;

public record ReportResponse(
        Long id,
        Long predictionId,
        String reportTitle,
        String reportHtml,
        LocalDateTime createdAt) {
}
