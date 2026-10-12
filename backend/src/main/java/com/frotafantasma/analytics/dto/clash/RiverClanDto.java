package com.frotafantasma.analytics.dto.clash;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RiverClanDto(
        String tag,
        String name,
        Integer badgeId,
        Integer fame,
        Integer repairPoints,
        Integer periodPoints,
        Integer clanScore,
        String finishTime,
        java.util.List<RiverParticipantDto> participants
) {
}
