package com.frotafantasma.analytics.repository;

import com.frotafantasma.analytics.entity.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PlayerRepository extends JpaRepository<Player, Long> {
    java.util.Optional<Player> findByPlayerTag(String tag);
    java.util.List<Player> findByClanIdAndActiveTrue(Long clanId);
}
