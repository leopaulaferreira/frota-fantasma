package com.frotafantasma.analytics.clash.dto;

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
        Integer donationsReceived
) {
}

