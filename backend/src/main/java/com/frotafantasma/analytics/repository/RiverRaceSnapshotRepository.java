package com.frotafantasma.analytics.repository;

import com.frotafantasma.analytics.entity.RiverRaceSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface RiverRaceSnapshotRepository extends JpaRepository<RiverRaceSnapshot, Long> {
    java.util.Optional<RiverRaceSnapshot> findFirstByClanIdOrderByCapturedAtDescIdDesc(Long clanId);
    Page<RiverRaceSnapshot> findByClanIdOrderByCapturedAtDescIdDesc(Long clanId, Pageable pageable);
}
