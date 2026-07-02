package com.hbut.medrisk.repository;

import com.hbut.medrisk.entity.EmailVerificationCodeEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailVerificationCodeRepository extends JpaRepository<EmailVerificationCodeEntity, Long> {
    Optional<EmailVerificationCodeEntity> findTopByEmailAndPurposeAndConsumedFalseOrderByCreatedAtDesc(String email, String purpose);
}
