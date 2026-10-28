package com.frotafantasma.analytics.analytics;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class AnalyticsDtos {
    private AnalyticsDtos() {}
    public enum Origin { DIRECT_API, ROYALEAPI_IMPORT, DERIVED, SNAPSHOT_DERIVED, UNAVAILABLE }
    public enum Quality { AVAILABLE, INSUFFICIENT_DATA, INCOMPLETE, UNAVAILABLE }
    public record Period(Instant from, Instant to, String basis) {}
    public record Metric<T>(T value, Origin origin, Quality quality, Period period, int warsConsidered,
                            boolean insufficientSample, boolean incompletePeriod, List<String> dataUsed, String reason) {
        @com.fasterxml.jackson.annotation.JsonProperty(value="sources",access=com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
        public List<Origin> sources() {
            var sources=new java.util.LinkedHashSet<Origin>();
            for(var source:List.of(Origin.DIRECT_API,Origin.ROYALEAPI_IMPORT))
                if(origin==source || dataUsed.stream().anyMatch(e->e.contains("source="+source.name()))) sources.add(source);
            return List.copyOf(sources);
        }
    }
    public record WarStats(Long warId, Integer seasonId, Integer sectionIndex, Instant publishedAt,
                           Metric<Integer> points, Metric<Integer> decksUsed, Metric<String> participation, Metric<Integer> rank) {}
    public record DonationPeriod(LocalDate week, Metric<Long> observedDonations, int snapshots, int detectedResets, Metric<Integer> lastCounter, Instant counterCapturedAt) {}
    public record PlayerStats(String tag, String name, String role, boolean currentMember,
                              Map<String, Metric<?>> metrics, List<WarStats> wars, List<DonationPeriod> donations,
                              Metric<String> participation, Metric<String> historicalContext,
                              Metric<String> operationalStatus, Metric<Boolean> elderCandidate) {}
    public record Highlight(String title, String playerTag, Metric<?> result) {}
    public record WeekEvolution(LocalDate week, Map<String, Metric<?>> metrics) {}
    public record Report(Instant generatedAt, LocalDate week, String ruleVersion, AnalyticsProperties rules,
                         List<PlayerStats> players, Map<String, Metric<?>> clan,
                         List<Highlight> highlights, List<WeekEvolution> evolution,
                         com.frotafantasma.analytics.recommendation.RecommendationDtos.RuleVersion recommendationRules) {
        public Report(Instant generatedAt, LocalDate week, String ruleVersion, AnalyticsProperties rules,
                      List<PlayerStats> players, Map<String, Metric<?>> clan,
                      List<Highlight> highlights, List<WeekEvolution> evolution) {
            this(generatedAt, week, ruleVersion, rules, players, clan, highlights, evolution, null);
        }
    }
}
