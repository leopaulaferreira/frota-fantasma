package com.frotafantasma.analytics.dto.clash;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClanApiDto(
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

