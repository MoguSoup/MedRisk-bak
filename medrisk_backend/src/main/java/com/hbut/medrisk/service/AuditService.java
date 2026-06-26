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
        AuditLogEntity log = new AuditLogEntity();
        log.setUserId(userId);
        log.setAction(action);
        log.setResourceType(resourceType);
        log.setResourceId(resourceId);
        log.setDetailJson(detailJson);
        log.setCreatedAt(LocalDateTime.now());
        auditLogs.save(log);
    }
}
