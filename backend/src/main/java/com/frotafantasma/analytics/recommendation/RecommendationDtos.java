package com.frotafantasma.analytics.recommendation;

import com.fasterxml.jackson.databind.JsonNode;
import com.frotafantasma.analytics.analytics.AnalyticsProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class RecommendationDtos {
    private RecommendationDtos() {}
    public record ScoreSettings(boolean enabled,
            @Min(0) @Max(100) int warWeight, @Min(0) @Max(100) int decksWeight,
            @Min(0) @Max(100) int donationsWeight,
            @Min(1) int warTarget, @Min(1) int donationTarget) {
        @com.fasterxml.jackson.annotation.JsonIgnore
        @AssertTrue(message = "Os pesos do Frota Score devem somar 100")
        public boolean isWeightSumValid() { return warWeight + decksWeight + donationsWeight == 100; }
        public static ScoreSettings defaults() { return new ScoreSettings(false, 45, 35, 20, 3200, 200); }
    }
    public record Settings(@NotNull @Valid AnalyticsProperties analytics, @NotNull @Valid ScoreSettings score,
                           @Min(1) @Max(365) int recentAlertDays, boolean immortalAllowPartial) {}
    public record RuleVersion(UUID id, int revision, Instant createdAt, String reason, Settings settings) {}
    public record RuleChange(UUID expectedVersion, @NotNull @Valid Settings settings,
                             @NotBlank @Size(max = 500) String reason) {}
    public record EvaluateRequest(LocalDate week, @NotBlank @Size(max = 100) String idempotencyKey) {}
    public record Evaluation(UUID id, LocalDate week, Instant createdAt, Instant analysisCutoff,
                             UUID ruleVersionId, String formulaVersion, JsonNode report) {}
    public record EvaluationSummary(UUID id, LocalDate week, Instant createdAt, UUID ruleVersionId, String formulaVersion) {}
    public record MemberStatus(UUID evaluationId, LocalDate week, Instant createdAt, Instant analysisCutoff,
                               String playerTag, String status, String color, JsonNode evidence) {}
    public record Alert(UUID evaluationId, String playerTag, String status, Instant analysisCutoff) {}
    public record FinalAward(String title, String playerTag, JsonNode evidence) {}
    public record AwardBatch(LocalDate week, UUID evaluationId, Instant finalizedAt, String timezone,
                             List<FinalAward> awards) {}
    public record ReceivedAward(LocalDate week, UUID evaluationId, Instant finalizedAt,
                                String title, String playerTag, JsonNode evidence) {}
    public record Page<T>(List<T> items, int page, int size, long totalElements) {}
}
