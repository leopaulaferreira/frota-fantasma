package com.frotafantasma.analytics.entity;

import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import java.util.UUID;

@MappedSuperclass
public abstract class HistoricalSource {
    private String source = "DIRECT_API";
    private UUID batchId;
    private Long resolutionId;
    private Instant importedAt;
    public String getSource() { return source; }
    public UUID getBatchId() { return batchId; }
    public Long getResolutionId() { return resolutionId; }
    public Instant getImportedAt() { return importedAt; }
    public String provenance() {
        return "source=" + source + (batchId == null ? "" : ";batch=" + batchId + ";resolution=" + resolutionId + ";importedAt=" + importedAt);
    }
}
