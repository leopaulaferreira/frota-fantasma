package com.frotafantasma.analytics.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "membership_event")
public class MembershipEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long playerId;
    private Long clanId;
    private UUID runId;
    private String type;
    private String previousRole;
    @Column(name = "role_current")
    private String currentRole;
    private Instant observedAt;

    protected MembershipEvent() {}

    public MembershipEvent(Long id, Long playerId, Long clanId, UUID runId, String type, String previousRole, String currentRole, Instant observedAt) {
        this.id = id;
        this.playerId = playerId;
        this.clanId = clanId;
        this.runId = runId;
        this.type = type;
        this.previousRole = previousRole;
        this.currentRole = currentRole;
        this.observedAt = observedAt;
    }

    public Long getId() { return id; }
    public Long getPlayerId() { return playerId; }
    public Long getClanId() { return clanId; }
    public UUID getRunId() { return runId; }
    public String getType() { return type; }
    public String getPreviousRole() { return previousRole; }
    public String getCurrentRole() { return currentRole; }
    public Instant getObservedAt() { return observedAt; }
}
