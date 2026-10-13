package com.frotafantasma.analytics.repository;

import com.frotafantasma.analytics.entity.ClanMembershipSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MembershipHistoryRepository extends JpaRepository<ClanMembershipSnapshot, Long> {
    java.util.List<ClanMembershipSnapshot> findByClanIdOrderByCapturedAtAscIdAsc(Long clanId);
    Page<ClanMembershipSnapshot> findByPlayerIdOrderByCapturedAtDescIdDesc(Long playerId, Pageable pageable);
    java.util.List<ClanMembershipSnapshot> findByRunId(java.util.UUID runId);
    java.util.Optional<ClanMembershipSnapshot> findFirstByClanIdOrderByCapturedAtDescIdDesc(Long clanId);
}
