package com.frotafantasma.analytics.snapshot;

import com.frotafantasma.analytics.clash.dto.ClanApiDto;
import com.frotafantasma.analytics.clash.dto.ClanMemberApiDto;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SnapshotStore {
    private final ClanRepository clans;
    private final ClanMemberRepository members;

    public SnapshotStore(ClanRepository clans, ClanMemberRepository members) {
        this.clans = clans;
        this.members = members;
    }

    @Transactional
    public void saveClan(ClanApiDto clan) {
        clans.save(new ClanEntity(clan, Instant.now()));
    }

    @Transactional
    public void replaceMembers(String clanTag, List<ClanMemberApiDto> currentMembers) {
        Instant observedAt = Instant.now();
        members.deleteByClanTag(clanTag);
        members.saveAll(currentMembers.stream()
                .map(member -> new ClanMemberEntity(clanTag, member, observedAt))
                .toList());
    }
}

