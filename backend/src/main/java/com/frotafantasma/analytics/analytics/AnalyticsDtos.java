package com.frotafantasma.analytics.analytics;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class AnalyticsDtos {
    private AnalyticsDtos() {}
    public enum Origin { DIRECT_API, DERIVED, SNAPSHOT_DERIVED, UNAVAILABLE }
    public enum Quality { AVAILABLE, INSUFFICIENT_DATA, INCOMPLETE, UNAVAILABLE }
    public record Period(Instant from, Instant to, String basis) {}
    public record Metric<T>(T value, Origin origin, Quality quality, Period period, int warsConsidered,
                            boolean insufficientSample, boolean incompletePeriod, List<String> dataUsed, String reason) {}
    public record WarStats(Long warId, Integer seasonId, Integer sectionIndex, Instant publishedAt,
                           Metric<Integer> points, Metric<Integer> decksUsed, Metric<String> participation, Metric<Integer> rank) {}
    public record DonationPeriod(LocalDate week, Metric<Long> observedDonations, int snapshots, int detectedResets) {}
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
