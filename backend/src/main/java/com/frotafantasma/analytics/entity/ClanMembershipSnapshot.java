package com.frotafantasma.analytics.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "clan_membership_history")
public class ClanMembershipSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long playerId;
    private Long clanId;
    private UUID runId;
    private String name;
    private String role;
    private Integer trophies;
    private Integer clanRank;
    private Integer previousClanRank;
    private Integer donations;
    private Integer donationsReceived;
    private Integer expLevel;
    private Instant capturedAt;

    protected ClanMembershipSnapshot() {}

    public ClanMembershipSnapshot(Long id, Long playerId, Long clanId, UUID runId, String name, String role, Integer trophies, Integer clanRank, Integer previousClanRank, Integer donations, Integer donationsReceived, Integer expLevel, Instant capturedAt) {
        this.id = id;
        this.playerId = playerId;
        this.clanId = clanId;
        this.runId = runId;
        this.name = name;
        this.role = role;
        this.trophies = trophies;
        this.clanRank = clanRank;
        this.previousClanRank = previousClanRank;
        this.donations = donations;
        this.donationsReceived = donationsReceived;
        this.expLevel = expLevel;
        this.capturedAt = capturedAt;
    }

    public Long getId() { return id; }
    public Long getPlayerId() { return playerId; }
    public Long getClanId() { return clanId; }
    public UUID getRunId() { return runId; }
    public String getName() { return name; }
    public String getRole() { return role; }
    public Integer getTrophies() { return trophies; }
    public Integer getClanRank() { return clanRank; }
    public Integer getPreviousClanRank() { return previousClanRank; }
    public Integer getDonations() { return donations; }
    public Integer getDonationsReceived() { return donationsReceived; }
    public Integer getExpLevel() { return expLevel; }
    public Instant getCapturedAt() { return capturedAt; }
}
