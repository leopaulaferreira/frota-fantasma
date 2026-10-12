package com.frotafantasma.analytics.dto.clash;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RiverLogEntryDto(
        Integer seasonId,
        Integer sectionIndex,
        String createdDate,
        java.util.List<RiverStandingDto> standings
) {
}
