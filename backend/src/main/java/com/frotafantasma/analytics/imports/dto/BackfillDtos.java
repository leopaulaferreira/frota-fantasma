package com.frotafantasma.analytics.imports.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class BackfillDtos {
    private BackfillDtos() {}
    public record Row(int line, String playerTag, String outcome, String reason, JsonNode values) {}
    public record Plan(UUID batchId, long resolutionId, int rowsAnalyzed, int rowsNew, int rowsDuplicated,
                       int rowsConflicted, int rowsIgnored, int playersAffected, Instant periodFrom, Instant periodTo,
                       String evidenceVersion, List<Row> rows, List<String> warnings, Result previous) {}
    public record Result(UUID id, UUID batchId, long resolutionId, Instant appliedAt, String appliedBy,
                         String status, int rowsNew, int rowsDuplicated, int rowsConflicted, int rowsIgnored, List<Row> rows) {}
    public record Confirm(@Positive long resolutionId, @NotBlank String evidenceVersion, boolean confirmed) {}
    public record Summary(String status, int rowsNew, int rowsDuplicated, int rowsConflicted, int rowsIgnored,
                          boolean influencesAnalytics, long activeRecords) {}
}
