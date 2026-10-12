package com.frotafantasma.analytics.dto.clash;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RiverParticipantDto(
        String tag,
        String name,
        Integer fame,
        Integer repairPoints,
        Integer boatAttacks,
        Integer decksUsed,
        Integer decksUsedToday
) {
}
