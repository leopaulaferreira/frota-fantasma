package com.frotafantasma.analytics.service;

import com.frotafantasma.analytics.repository.ClanRepository;
import com.frotafantasma.analytics.repository.ClanMemberRepository;
import com.frotafantasma.analytics.mapper.ClanMapper;
import com.frotafantasma.analytics.dto.clash.ClanApiDto;
import com.frotafantasma.analytics.dto.clash.ClanMemberApiDto;
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
        clans.save(ClanMapper.toEntity(clan, Instant.now()));
    }

    @Transactional
    public void replaceMembers(String clanTag, List<ClanMemberApiDto> currentMembers) {
        Instant observedAt = Instant.now();
        members.deleteByClanTag(clanTag);
        members.saveAll(currentMembers.stream()
                .map(member -> ClanMapper.toEntity(clanTag, member, observedAt))
                .toList());
    }
}

