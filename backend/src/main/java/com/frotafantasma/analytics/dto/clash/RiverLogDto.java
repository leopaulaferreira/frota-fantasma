package com.frotafantasma.analytics.dto.clash;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RiverLogDto(
        java.util.List<RiverLogEntryDto> items,
        PagingDto paging
) {
}
