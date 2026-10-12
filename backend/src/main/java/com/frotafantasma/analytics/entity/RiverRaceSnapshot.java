package com.frotafantasma.analytics.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "river_race_snapshot")
public class RiverRaceSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long clanId;
    private UUID runId;
    private Integer sectionIndex;
    private Integer periodIndex;
    private String periodType;
    private String state;
    private Instant capturedAt;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode payload;

    protected RiverRaceSnapshot() {}

    public RiverRaceSnapshot(Long id, Long clanId, UUID runId, Integer sectionIndex, Integer periodIndex, String periodType, String state, Instant capturedAt, JsonNode payload) {
        this.id = id;
        this.clanId = clanId;
        this.runId = runId;
        this.sectionIndex = sectionIndex;
        this.periodIndex = periodIndex;
        this.periodType = periodType;
        this.state = state;
        this.capturedAt = capturedAt;
        this.payload = payload;
    }

    public Long getId() { return id; }
    public Long getClanId() { return clanId; }
    public UUID getRunId() { return runId; }
    public Integer getSectionIndex() { return sectionIndex; }
    public Integer getPeriodIndex() { return periodIndex; }
    public String getPeriodType() { return periodType; }
    public String getState() { return state; }
    public Instant getCapturedAt() { return capturedAt; }
    public JsonNode getPayload() { return payload; }
}
