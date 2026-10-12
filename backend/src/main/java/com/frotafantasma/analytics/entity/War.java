package com.frotafantasma.analytics.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "war")
public class War {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long clanId;
    private Integer seasonId;
    private Integer sectionIndex;
    private Instant publishedAt;
    private Instant startDate;
    private Instant endDate;
    private Integer clanPlacement;
    private Integer clanScore;
    private Integer fame;
    private Integer clanTrophyChange;
    private Instant capturedAt;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode payload;

    protected War() {}

    public War(Long id, Long clanId, Integer seasonId, Integer sectionIndex, Instant publishedAt, Instant startDate, Instant endDate, Integer clanPlacement, Integer clanScore, Integer fame, Integer clanTrophyChange, Instant capturedAt, JsonNode payload) {
        this.id = id;
        this.clanId = clanId;
        this.seasonId = seasonId;
        this.sectionIndex = sectionIndex;
        this.publishedAt = publishedAt;
        this.startDate = startDate;
        this.endDate = endDate;
        this.clanPlacement = clanPlacement;
        this.clanScore = clanScore;
        this.fame = fame;
        this.clanTrophyChange = clanTrophyChange;
        this.capturedAt = capturedAt;
        this.payload = payload;
    }

    public Long getId() { return id; }
    public Long getClanId() { return clanId; }
    public Integer getSeasonId() { return seasonId; }
    public Integer getSectionIndex() { return sectionIndex; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getStartDate() { return startDate; }
    public Instant getEndDate() { return endDate; }
    public Integer getClanPlacement() { return clanPlacement; }
    public Integer getClanScore() { return clanScore; }
    public Integer getFame() { return fame; }
    public Integer getClanTrophyChange() { return clanTrophyChange; }
    public Instant getCapturedAt() { return capturedAt; }
    public JsonNode getPayload() { return payload; }
}
