package com.frotafantasma.analytics.repository;

import com.frotafantasma.analytics.entity.ClanEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClanRepository extends JpaRepository<ClanEntity, String> {
}

