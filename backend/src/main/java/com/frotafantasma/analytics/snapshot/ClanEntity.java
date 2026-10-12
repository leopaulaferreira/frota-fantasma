package com.frotafantasma.analytics.snapshot;

import com.frotafantasma.analytics.clash.dto.ClanApiDto;
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

    public ClanEntity(ClanApiDto source, Instant observedAt) {
        this.tag = source.tag();
        this.name = source.name();
        this.description = source.description();
        this.type = source.type();
        this.clanScore = source.clanScore();
        this.clanWarTrophies = source.clanWarTrophies();
        this.requiredTrophies = source.requiredTrophies();
        this.donationsPerWeek = source.donationsPerWeek();
        this.members = source.members();
        this.observedAt = observedAt;
    }
}

