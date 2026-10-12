package com.frotafantasma.analytics.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "collection_run")
public class CollectionRun {
    @Id
    private UUID id;
    private String clanTag;
    private String kind;
    private String status;
    private Instant startedAt;
    private Instant completedAt;
    private String errorCode;
    private Integer recordCount;
    private String deduplicationKey;

    protected CollectionRun() {}

    public CollectionRun(UUID id, String clanTag, String kind, String status, Instant startedAt, Instant completedAt, String errorCode, Integer recordCount, String deduplicationKey) {
        this.id = id;
        this.clanTag = clanTag;
        this.kind = kind;
        this.status = status;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.errorCode = errorCode;
        this.recordCount = recordCount;
        this.deduplicationKey = deduplicationKey;
    }

    public UUID getId() { return id; }
    public String getClanTag() { return clanTag; }
    public String getKind() { return kind; }
    public String getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getErrorCode() { return errorCode; }
    public Integer getRecordCount() { return recordCount; }
    public String getDeduplicationKey() { return deduplicationKey; }
}
