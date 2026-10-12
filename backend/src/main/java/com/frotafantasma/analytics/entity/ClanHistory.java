package com.frotafantasma.analytics.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "clan_history")
public class ClanHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long clanId;
    private UUID runId;
    private String name;
    @Column(columnDefinition = "text")
    private String description;
    private String type;
    private Integer clanScore;
    private Integer clanWarTrophies;
    private Integer requiredTrophies;
    private Integer donationsPerWeek;
    private Integer memberCount;
    private Instant capturedAt;

    protected ClanHistory() {}

    public ClanHistory(Long id, Long clanId, UUID runId, String name, String description, String type, Integer clanScore, Integer clanWarTrophies, Integer requiredTrophies, Integer donationsPerWeek, Integer memberCount, Instant capturedAt) {
        this.id = id;
        this.clanId = clanId;
        this.runId = runId;
        this.name = name;
        this.description = description;
        this.type = type;
        this.clanScore = clanScore;
        this.clanWarTrophies = clanWarTrophies;
        this.requiredTrophies = requiredTrophies;
        this.donationsPerWeek = donationsPerWeek;
        this.memberCount = memberCount;
        this.capturedAt = capturedAt;
    }

    public Long getId() { return id; }
    public Long getClanId() { return clanId; }
    public UUID getRunId() { return runId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getType() { return type; }
    public Integer getClanScore() { return clanScore; }
    public Integer getClanWarTrophies() { return clanWarTrophies; }
    public Integer getRequiredTrophies() { return requiredTrophies; }
    public Integer getDonationsPerWeek() { return donationsPerWeek; }
    public Integer getMemberCount() { return memberCount; }
    public Instant getCapturedAt() { return capturedAt; }
}
