package com.frotafantasma.analytics.repository;

import com.frotafantasma.analytics.entity.War;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface WarRepository extends JpaRepository<War, Long> {
    java.util.Optional<War> findByClanIdAndSeasonIdAndSectionIndex(Long clanId, Integer seasonId, Integer sectionIndex);
    Page<War> findByClanIdOrderByPublishedAtDescIdDesc(Long clanId, Pageable pageable);
}
