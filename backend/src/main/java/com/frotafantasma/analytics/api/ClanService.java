package com.frotafantasma.analytics.api;

import com.frotafantasma.analytics.api.dto.ClanMemberResponse;
import com.frotafantasma.analytics.api.dto.ClanResponse;
import com.frotafantasma.analytics.clash.ClashRoyaleClient;
import com.frotafantasma.analytics.clash.ClashRoyaleException;
import com.frotafantasma.analytics.clash.ClashRoyaleProperties;
import com.frotafantasma.analytics.clash.dto.ClanApiDto;
import com.frotafantasma.analytics.clash.dto.ClanMembersApiDto;
import com.frotafantasma.analytics.snapshot.SnapshotStore;
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
        return ClanResponse.from(clan);
    }

    public List<ClanMemberResponse> getMembers() {
        ClanMembersApiDto response = client.getMembers();
        if (response.items() == null || response.items().stream()
                .anyMatch(member -> member == null || member.tag() == null || member.tag().isBlank())) {
            throw invalidResponse();
        }
        snapshots.replaceMembers(properties.clanTag(), response.items());
        return response.items().stream().map(ClanMemberResponse::from).toList();
    }

    private ClashRoyaleException invalidResponse() {
        return new ClashRoyaleException(HttpStatus.BAD_GATEWAY, "INVALID_UPSTREAM_RESPONSE",
                "A API do Clash Royale retornou dados inválidos.");
    }
}

