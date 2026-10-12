package com.frotafantasma.analytics.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "player")
public class Player {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String playerTag;
    private String name;
    private Long clanId;
    private Instant firstSeenAt;
    private Instant lastSeenAt;
    private boolean active;
    @Column(name = "role_current")
    private String currentRole;

    protected Player() {}

    public Player(Long id, String playerTag, String name, Long clanId, Instant firstSeenAt, Instant lastSeenAt, boolean active, String currentRole) {
        this.id = id;
        this.playerTag = playerTag;
        this.name = name;
        this.clanId = clanId;
        this.firstSeenAt = firstSeenAt;
        this.lastSeenAt = lastSeenAt;
        this.active = active;
        this.currentRole = currentRole;
    }

    public Long getId() { return id; }
    public String getPlayerTag() { return playerTag; }
    public String getName() { return name; }
    public Long getClanId() { return clanId; }
    public Instant getFirstSeenAt() { return firstSeenAt; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public boolean getActive() { return active; }
    public String getCurrentRole() { return currentRole; }
}
