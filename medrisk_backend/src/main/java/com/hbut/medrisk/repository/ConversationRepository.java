package com.hbut.medrisk.repository;

import com.hbut.medrisk.entity.ConversationEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<ConversationEntity, Long> {
    List<ConversationEntity> findTop100ByUserIdOrderByUpdatedAtDesc(Long userId);
    boolean existsByUserId(Long userId);
}
