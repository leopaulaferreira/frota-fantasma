package com.frotafantasma.analytics.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "clan")
public class Clan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String tag;
    private String name;
    @Column(columnDefinition = "text")
    private String description;
    private String type;
    private Integer clanScore;
    private Integer clanWarTrophies;
    private Integer requiredTrophies;
    private Integer donationsPerWeek;
    private Integer memberCount;
    private Instant snapshotDate;
    private boolean baselineEstablished;

    protected Clan() {}

    public Clan(Long id, String tag, String name, String description, String type, Integer clanScore, Integer clanWarTrophies, Integer requiredTrophies, Integer donationsPerWeek, Integer memberCount, Instant snapshotDate, boolean baselineEstablished) {
        this.id = id;
        this.tag = tag;
        this.name = name;
        this.description = description;
        this.type = type;
        this.clanScore = clanScore;
        this.clanWarTrophies = clanWarTrophies;
        this.requiredTrophies = requiredTrophies;
        this.donationsPerWeek = donationsPerWeek;
        this.memberCount = memberCount;
        this.snapshotDate = snapshotDate;
        this.baselineEstablished = baselineEstablished;
    }

    public Long getId() { return id; }
    public String getTag() { return tag; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getType() { return type; }
    public Integer getClanScore() { return clanScore; }
    public Integer getClanWarTrophies() { return clanWarTrophies; }
    public Integer getRequiredTrophies() { return requiredTrophies; }
    public Integer getDonationsPerWeek() { return donationsPerWeek; }
    public Integer getMemberCount() { return memberCount; }
    public Instant getSnapshotDate() { return snapshotDate; }
    public boolean getBaselineEstablished() { return baselineEstablished; }
}
