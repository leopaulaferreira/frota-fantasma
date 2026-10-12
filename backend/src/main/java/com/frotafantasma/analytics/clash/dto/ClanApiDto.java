package com.frotafantasma.analytics.clash.dto;

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

