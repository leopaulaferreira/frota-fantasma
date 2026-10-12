package com.frotafantasma.analytics.entity;

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

    public ClanMemberEntity(
            String tag,
            String clanTag,
            String name,
            String role,
            Integer expLevel,
            Integer trophies,
            Integer clanRank,
            Integer previousClanRank,
            Integer donations,
            Integer donationsReceived,
            Instant observedAt
    ) {
        this.tag = tag;
        this.clanTag = clanTag;
        this.name = name;
        this.role = role;
        this.expLevel = expLevel;
        this.trophies = trophies;
        this.clanRank = clanRank;
        this.previousClanRank = previousClanRank;
        this.donations = donations;
        this.donationsReceived = donationsReceived;
        this.observedAt = observedAt;
    }
}
