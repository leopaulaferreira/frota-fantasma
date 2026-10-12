package com.frotafantasma.analytics.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "war_participant")
public class WarParticipant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long warId;
    private Long playerId;
    private String name;
    private Integer contribution;
    private Integer decksUsed;
    private Integer decksUsedToday;
    private Integer boatAttacks;
    private Integer repairPoints;
    private Integer battlesPlayed;
    private Integer wins;
    private Integer losses;
    private Instant capturedAt;

    protected WarParticipant() {}

    public WarParticipant(Long id, Long warId, Long playerId, String name, Integer contribution, Integer decksUsed, Integer decksUsedToday, Integer boatAttacks, Integer repairPoints, Integer battlesPlayed, Integer wins, Integer losses, Instant capturedAt) {
        this.id = id;
        this.warId = warId;
        this.playerId = playerId;
        this.name = name;
        this.contribution = contribution;
        this.decksUsed = decksUsed;
        this.decksUsedToday = decksUsedToday;
        this.boatAttacks = boatAttacks;
        this.repairPoints = repairPoints;
        this.battlesPlayed = battlesPlayed;
        this.wins = wins;
        this.losses = losses;
        this.capturedAt = capturedAt;
    }

    public Long getId() { return id; }
    public Long getWarId() { return warId; }
    public Long getPlayerId() { return playerId; }
    public String getName() { return name; }
    public Integer getContribution() { return contribution; }
    public Integer getDecksUsed() { return decksUsed; }
    public Integer getDecksUsedToday() { return decksUsedToday; }
    public Integer getBoatAttacks() { return boatAttacks; }
    public Integer getRepairPoints() { return repairPoints; }
    public Integer getBattlesPlayed() { return battlesPlayed; }
    public Integer getWins() { return wins; }
    public Integer getLosses() { return losses; }
    public Instant getCapturedAt() { return capturedAt; }
}
