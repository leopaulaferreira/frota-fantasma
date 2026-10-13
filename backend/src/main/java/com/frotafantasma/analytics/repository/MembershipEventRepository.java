package com.frotafantasma.analytics.repository;

import com.frotafantasma.analytics.entity.MembershipEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MembershipEventRepository extends JpaRepository<MembershipEvent, Long> {
    java.util.List<MembershipEvent> findByClanIdOrderByObservedAtAscIdAsc(Long clanId);
    Page<MembershipEvent> findByClanIdOrderByObservedAtDescIdDesc(Long clanId, Pageable pageable);
    Page<MembershipEvent> findByPlayerIdOrderByObservedAtDescIdDesc(Long playerId, Pageable pageable);
}
