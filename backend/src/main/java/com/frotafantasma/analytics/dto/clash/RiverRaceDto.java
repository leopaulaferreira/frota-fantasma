package com.frotafantasma.analytics.dto.clash;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RiverRaceDto(
        String state,
        RiverClanDto clan,
        java.util.List<RiverClanDto> clans,
        Integer sectionIndex,
        Integer periodIndex,
        String periodType,
        com.fasterxml.jackson.databind.JsonNode periodLogs
) {
}
