package com.hbut.medrisk.repository;

import com.hbut.medrisk.entity.DataSeedRunEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DataSeedRunRepository extends JpaRepository<DataSeedRunEntity, Long> {
    Optional<DataSeedRunEntity> findFirstBySeedKeyOrderByStartedAtDesc(String seedKey);
    List<DataSeedRunEntity> findTop20ByOrderByStartedAtDesc();
}
