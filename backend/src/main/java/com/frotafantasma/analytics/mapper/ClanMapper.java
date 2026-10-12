package com.frotafantasma.analytics.mapper;

import com.frotafantasma.analytics.dto.ClanMemberResponse;
import com.frotafantasma.analytics.dto.ClanResponse;
import com.frotafantasma.analytics.dto.clash.ClanApiDto;
import com.frotafantasma.analytics.dto.clash.ClanMemberApiDto;
import com.frotafantasma.analytics.entity.ClanEntity;
import com.frotafantasma.analytics.entity.ClanMemberEntity;
import java.time.Instant;

public final class ClanMapper {
    private ClanMapper() {
    }

    public static ClanResponse toResponse(ClanApiDto source) {
        return new ClanResponse(source.tag(), source.name(), source.description(), source.type(),
                source.clanScore(), source.clanWarTrophies(), source.requiredTrophies(),
                source.donationsPerWeek(), source.members());
    }

    public static ClanMemberResponse toResponse(ClanMemberApiDto source) {
        return new ClanMemberResponse(source.tag(), source.name(), source.role(), source.expLevel(),
                source.trophies(), source.clanRank(), source.previousClanRank(),
                source.donations(), source.donationsReceived());
    }

    public static ClanEntity toEntity(ClanApiDto source, Instant observedAt) {
        return new ClanEntity(source.tag(), source.name(), source.description(), source.type(),
                source.clanScore(), source.clanWarTrophies(), source.requiredTrophies(),
                source.donationsPerWeek(), source.members(), observedAt);
    }

    public static ClanMemberEntity toEntity(String clanTag, ClanMemberApiDto source, Instant observedAt) {
        return new ClanMemberEntity(source.tag(), clanTag, source.name(), source.role(), source.expLevel(),
                source.trophies(), source.clanRank(), source.previousClanRank(),
                source.donations(), source.donationsReceived(), observedAt);
    }
}
