package com.frotafantasma.analytics.dto.clash;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RiverStandingDto(
        Integer rank,
        Integer trophyChange,
        RiverClanDto clan
) {
}
