package com.hbut.medrisk.repository;

import com.hbut.medrisk.entity.PatientProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientProfileRepository extends JpaRepository<PatientProfileEntity, Long> {
}
