package com.frotafantasma.analytics.service;

import com.frotafantasma.analytics.mapper.ClanMapper;
import com.frotafantasma.analytics.dto.ClanMemberResponse;
import com.frotafantasma.analytics.dto.ClanResponse;
import com.frotafantasma.analytics.client.ClashRoyaleClient;
import com.frotafantasma.analytics.client.ClashRoyaleException;
import com.frotafantasma.analytics.config.ClashRoyaleProperties;
import com.frotafantasma.analytics.dto.clash.ClanApiDto;
import com.frotafantasma.analytics.dto.clash.ClanMembersApiDto;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ClanService {
    private final ClashRoyaleClient client;
    private final ClashRoyaleProperties properties;
    private final SnapshotStore snapshots;

    public ClanService(ClashRoyaleClient client, ClashRoyaleProperties properties, SnapshotStore snapshots) {
        this.client = client;
        this.properties = properties;
        this.snapshots = snapshots;
    }

    public ClanResponse getClan() {
        ClanApiDto clan = client.getClan();
        if (clan.tag() == null || clan.tag().isBlank()) {
            throw invalidResponse();
        }
        snapshots.saveClan(clan);
        return ClanMapper.toResponse(clan);
    }

    public List<ClanMemberResponse> getMembers() {
        ClanMembersApiDto response = client.getMembers();
        if (response.items() == null || response.items().stream()
                .anyMatch(member -> member == null || member.tag() == null || member.tag().isBlank())) {
            throw invalidResponse();
        }
        snapshots.replaceMembers(properties.clanTag(), response.items());
        return response.items().stream().map(ClanMapper::toResponse).toList();
    }

    private ClashRoyaleException invalidResponse() {
        return new ClashRoyaleException(HttpStatus.BAD_GATEWAY, "INVALID_UPSTREAM_RESPONSE",
                "A API do Clash Royale retornou dados inválidos.");
    }
}

