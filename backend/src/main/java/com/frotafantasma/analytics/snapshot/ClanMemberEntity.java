package com.frotafantasma.analytics.snapshot;

import com.frotafantasma.analytics.clash.dto.ClanMemberApiDto;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "clan_member_snapshot")
public class ClanMemberEntity {
    @Id
    private String tag;
    private String clanTag;
    private String name;
    private String role;
    private Integer expLevel;
    private Integer trophies;
    private Integer clanRank;
    private Integer previousClanRank;
    private Integer donations;
    private Integer donationsReceived;
    private Instant observedAt;

    protected ClanMemberEntity() {
    }

    public ClanMemberEntity(String clanTag, ClanMemberApiDto source, Instant observedAt) {
        this.tag = source.tag();
        this.clanTag = clanTag;
        this.name = source.name();
        this.role = source.role();
        this.expLevel = source.expLevel();
        this.trophies = source.trophies();
        this.clanRank = source.clanRank();
        this.previousClanRank = source.previousClanRank();
        this.donations = source.donations();
        this.donationsReceived = source.donationsReceived();
        this.observedAt = observedAt;
    }
}

