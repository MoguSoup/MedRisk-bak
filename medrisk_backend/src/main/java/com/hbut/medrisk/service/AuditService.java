package com.hbut.medrisk.service;

import com.hbut.medrisk.entity.AuditLogEntity;
import com.hbut.medrisk.repository.AuditLogRepository;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final AuditLogRepository auditLogs;

    public AuditService(AuditLogRepository auditLogs) {
        this.auditLogs = auditLogs;
    }

    public void log(Long userId, String action, String resourceType, String resourceId, String detailJson) {
        log(userId, action, resourceType, resourceId, detailJson, RequestIpContext.get());
    }

    public void log(Long userId, String action, String resourceType, String resourceId, String detailJson, String clientIp) {
        AuditLogEntity log = new AuditLogEntity();
        log.setUserId(userId);
        log.setAction(action);
        log.setResourceType(resourceType);
        log.setResourceId(resourceId);
        log.setDetailJson(detailJson);
        log.setClientIp(cleanIp(clientIp == null ? RequestIpContext.get() : clientIp));
        log.setCreatedAt(LocalDateTime.now());
        auditLogs.save(log);
    }

    private String cleanIp(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return null;
        }
        String cleaned = clientIp.trim();
        return cleaned.length() > 45 ? cleaned.substring(0, 45) : cleaned;
    }
}
