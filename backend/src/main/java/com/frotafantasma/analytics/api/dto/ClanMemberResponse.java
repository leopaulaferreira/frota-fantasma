package com.frotafantasma.analytics.api.dto;

import com.frotafantasma.analytics.clash.dto.ClanMemberApiDto;

public record ClanMemberResponse(
        String tag,
        String name,
        String role,
        Integer expLevel,
        Integer trophies,
        Integer clanRank,
        Integer previousClanRank,
        Integer donations,
        Integer donationsReceived
) {
    public static ClanMemberResponse from(ClanMemberApiDto source) {
        return new ClanMemberResponse(
                source.tag(), source.name(), source.role(), source.expLevel(), source.trophies(),
                source.clanRank(), source.previousClanRank(), source.donations(), source.donationsReceived()
        );
    }
}

