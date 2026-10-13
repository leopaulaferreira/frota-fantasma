package com.frotafantasma.analytics.dto.clash;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClanMemberApiDto(
        String tag,
        String name,
        String role,
        Integer expLevel,
        Integer trophies,
        Integer clanRank,
        Integer previousClanRank,
        Integer donations,
        Integer donationsReceived,
        String lastSeen
) {
    public ClanMemberApiDto(String tag, String name, String role, Integer expLevel, Integer trophies,
            Integer clanRank, Integer previousClanRank, Integer donations, Integer donationsReceived) {
        this(tag, name, role, expLevel, trophies, clanRank, previousClanRank, donations, donationsReceived, null);
    }
}

