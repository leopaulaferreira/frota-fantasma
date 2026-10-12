package com.frotafantasma.analytics.dto;


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
}
