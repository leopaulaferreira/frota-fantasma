package com.frotafantasma.analytics.snapshot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClanMemberRepository extends JpaRepository<ClanMemberEntity, String> {
    @Modifying
    @Query("delete from ClanMemberEntity member where member.clanTag = :clanTag")
    void deleteByClanTag(@Param("clanTag") String clanTag);
}

