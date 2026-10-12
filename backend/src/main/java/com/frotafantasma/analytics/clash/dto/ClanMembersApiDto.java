package com.frotafantasma.analytics.clash.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClanMembersApiDto(List<ClanMemberApiDto> items) {
}

