package com.frotafantasma.analytics.dto.clash;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PagingDto(
        Cursors cursors
) {
    public record Cursors(String before, String after) {}
}
