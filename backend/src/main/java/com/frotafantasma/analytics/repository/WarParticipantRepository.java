package com.frotafantasma.analytics.repository;

import com.frotafantasma.analytics.entity.WarParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface WarParticipantRepository extends JpaRepository<WarParticipant, Long> {
    java.util.List<WarParticipant> findByWarIdIn(java.util.Collection<Long> warIds);
    java.util.List<WarParticipant> findByWarIdOrderByContributionDesc(Long warId);
    void deleteByWarId(Long warId);
    @org.springframework.data.jpa.repository.Query("select p from WarParticipant p join War w on p.warId = w.id where p.playerId = :playerId order by w.publishedAt desc, p.id desc")
    Page<WarParticipant> findHistoryByPlayerId(@org.springframework.data.repository.query.Param("playerId") Long playerId, Pageable pageable);
}
