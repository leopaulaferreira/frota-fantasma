package com.frotafantasma.analytics.repository;

import com.frotafantasma.analytics.entity.WarParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface WarParticipantRepository extends JpaRepository<WarParticipant, Long> {
    java.util.List<WarParticipant> findByWarIdOrderByContributionDesc(Long warId);
    void deleteByWarId(Long warId);
    Page<WarParticipant> findByPlayerIdOrderByCapturedAtDescIdDesc(Long playerId, Pageable pageable);
}
