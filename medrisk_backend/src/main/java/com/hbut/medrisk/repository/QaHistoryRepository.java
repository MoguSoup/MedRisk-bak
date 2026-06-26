package com.hbut.medrisk.repository;

import com.hbut.medrisk.entity.QaHistoryEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QaHistoryRepository extends JpaRepository<QaHistoryEntity, Long> {
    List<QaHistoryEntity> findByConversationIdOrderByCreatedAtAsc(Long conversationId);
    boolean existsByUserId(Long userId);
}
