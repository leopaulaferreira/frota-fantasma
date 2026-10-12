package com.frotafantasma.analytics.repository;

import com.frotafantasma.analytics.entity.ClanHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ClanHistoryRepository extends JpaRepository<ClanHistory, Long> {
    Page<ClanHistory> findByClanIdOrderByCapturedAtDescIdDesc(Long clanId, Pageable pageable);
}
