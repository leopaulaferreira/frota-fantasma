package com.frotafantasma.analytics.dto;


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
}
