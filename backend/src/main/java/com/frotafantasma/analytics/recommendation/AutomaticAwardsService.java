package com.frotafantasma.analytics.recommendation;

import static com.frotafantasma.analytics.recommendation.RecommendationDtos.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frotafantasma.analytics.analytics.AnalyticsService;
import com.frotafantasma.analytics.config.ClashRoyaleProperties;
import com.frotafantasma.analytics.repository.ClanDataRepository;
import com.frotafantasma.analytics.repository.WarRepository;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutomaticAwardsService {
    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private final RecommendationRepository repository;
    private final RecommendationRulesService rules;
    private final AnalyticsService analytics;
    private final ClanDataRepository clans;
    private final WarRepository wars;
    private final ClashRoyaleProperties api;
    private final Clock clock;
    private final ObjectMapper json;

    public AutomaticAwardsService(RecommendationRepository repository, RecommendationRulesService rules,
            AnalyticsService analytics, ClanDataRepository clans, WarRepository wars,
            ClashRoyaleProperties api, Clock clock, ObjectMapper json) {
        this.repository=repository; this.rules=rules; this.analytics=analytics; this.clans=clans;
        this.wars=wars; this.api=api; this.clock=clock; this.json=json;
    }
    public static LocalDate closingWeek(Instant now) {
        var local = now.atZone(ZONE);
        var monday = local.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return now.isBefore(monday.atTime(6,0).atZone(ZONE).toInstant()) ? monday.minusWeeks(1) : monday;
    }
    public boolean pending() { return repository.awards(api.clanTag(), closingWeek(clock.instant())).isEmpty(); }

    @Transactional
    public Optional<AwardBatch> publish() {
        LocalDate week = closingWeek(clock.instant());
        repository.lock(api.clanTag());
        var existing = repository.awards(api.clanTag(), week);
        if (existing.isPresent()) return existing;
        var clan = clans.findByTag(api.clanTag());
        if (clan.isEmpty()) return Optional.empty();
        var history = wars.findByClanIdOrderByPublishedAtDescIdDesc(clan.get().getId());
        Instant close = week.atTime(6,0).atZone(ZONE).toInstant();
        // The official war log, not merely the wall clock, must confirm this closing.
        if (history.isEmpty() || history.getFirst().getPublishedAt().isBefore(close)
                || !history.getFirst().getPublishedAt().isBefore(close.plus(Duration.ofDays(7)))
                || history.getFirst().getPublishedAt().isAfter(clock.instant())) return Optional.empty();
        var version = rules.ensureStored();
        var report = analytics.closingReport(week, version);
        // Never freeze the week's batch without the newly closed war's captain.
        if (report.highlights().stream().noneMatch(h -> h.title().equals("CAPITAO_DA_GUERRA")
                && h.playerTag()!=null && h.result().value()!=null && !h.result().insufficientSample())) return Optional.empty();
        var awards = new ArrayList<FinalAward>();
        var winners = new HashSet<String>();
        for (var h : report.highlights()) {
            if (h.playerTag()==null || h.result().value()==null || h.result().insufficientSample()) continue;
            if (!winners.add(h.playerTag())) throw new IllegalStateException("Duplicate award recipient");
            awards.add(new FinalAward(h.title(), h.playerTag(), json.valueToTree(h)));
        }
        var evaluation = new Evaluation(UUID.randomUUID(), week, clock.instant(), report.generatedAt(),
                version.id(), report.ruleVersion(), json.valueToTree(report));
        repository.insertEvaluation(api.clanTag(), "automatic-closing:" + week, evaluation);
        var batch = new AwardBatch(week, evaluation.id(), clock.instant(), ZONE.toString(), List.copyOf(awards));
        repository.insertAwards(api.clanTag(), batch);
        return repository.awards(api.clanTag(), week);
    }
}
