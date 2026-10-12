package com.frotafantasma.analytics.repository;

import com.frotafantasma.analytics.entity.Clan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ClanDataRepository extends JpaRepository<Clan, Long> {
    java.util.Optional<Clan> findByTag(String tag);
}
