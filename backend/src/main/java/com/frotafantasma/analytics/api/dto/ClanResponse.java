package com.frotafantasma.analytics.api.dto;

import com.frotafantasma.analytics.clash.dto.ClanApiDto;

public record ClanResponse(
        String tag,
        String name,
        String description,
        String type,
        Integer clanScore,
        Integer clanWarTrophies,
        Integer requiredTrophies,
        Integer donationsPerWeek,
        Integer members
) {
    public static ClanResponse from(ClanApiDto source) {
        return new ClanResponse(
                source.tag(), source.name(), source.description(), source.type(),
                source.clanScore(), source.clanWarTrophies(), source.requiredTrophies(),
                source.donationsPerWeek(), source.members()
        );
    }
}

