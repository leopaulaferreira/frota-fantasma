package com.frotafantasma.analytics.dto.clash;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Top-level Trophy Road fields only. Ranked and seasonal results are deliberately excluded. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlayerProfileDto(String tag, String name, Integer trophies, Integer bestTrophies, Clan clan) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Clan(String tag) {}
}
