package com.hbut.medrisk.repository;

import com.hbut.medrisk.entity.AuditLogEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {
    List<AuditLogEntity> findTop100ByOrderByCreatedAtDesc();
}
