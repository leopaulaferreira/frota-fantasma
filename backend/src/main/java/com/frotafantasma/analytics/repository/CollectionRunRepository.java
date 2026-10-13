package com.frotafantasma.analytics.repository;

import com.frotafantasma.analytics.entity.CollectionRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CollectionRunRepository extends JpaRepository<CollectionRun, java.util.UUID> {
    java.util.List<CollectionRun> findByClanTagAndStatus(String tag, String status);
    boolean existsByDeduplicationKeyAndStatus(String key, String status);
    Page<CollectionRun> findByClanTagOrderByStartedAtDesc(String tag, Pageable pageable);
}
