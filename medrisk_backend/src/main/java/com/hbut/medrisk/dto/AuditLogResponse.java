package com.hbut.medrisk.dto;

import java.time.LocalDateTime;

public record AuditLogResponse(
        Long id,
        Long userId,
        String action,
        String resourceType,
        String resourceId,
        String detailJson,
        LocalDateTime createdAt) {
}
