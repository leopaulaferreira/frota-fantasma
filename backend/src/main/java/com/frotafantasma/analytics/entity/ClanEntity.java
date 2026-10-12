package com.frotafantasma.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "clan_snapshot")
public class ClanEntity {
    @Id
    private String tag;
    private String name;
    @Column(columnDefinition = "text")
    private String description;
    private String type;
    private Integer clanScore;
    private Integer clanWarTrophies;
    private Integer requiredTrophies;
    private Integer donationsPerWeek;
    private Integer members;
    private Instant observedAt;

    protected ClanEntity() {
    }

    public ClanEntity(
            String tag,
            String name,
            String description,
            String type,
            Integer clanScore,
            Integer clanWarTrophies,
            Integer requiredTrophies,
            Integer donationsPerWeek,
            Integer members,
            Instant observedAt
    ) {
        this.tag = tag;
        this.name = name;
        this.description = description;
        this.type = type;
        this.clanScore = clanScore;
        this.clanWarTrophies = clanWarTrophies;
        this.requiredTrophies = requiredTrophies;
        this.donationsPerWeek = donationsPerWeek;
        this.members = members;
        this.observedAt = observedAt;
    }
}
