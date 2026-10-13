package com.frotafantasma.analytics.analytics;

import com.frotafantasma.analytics.config.ClashRoyaleProperties;
import com.frotafantasma.analytics.recommendation.*;
import com.frotafantasma.analytics.recommendation.RecommendationDtos.RuleVersion;
import org.springframework.transaction.annotation.Propagation;
import com.frotafantasma.analytics.entity.Player;
import com.frotafantasma.analytics.repository.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AnalyticsService {
    private final ClanDataRepository clans;
    private final PlayerRepository players;
    private final WarRepository wars;
    private final WarParticipantRepository participants;
    private final MembershipHistoryRepository snapshots;
    private final MembershipEventRepository events;
    private final ClanHistoryRepository clanSnapshots;
    private final ClashRoyaleProperties api;
    private final RecommendationRulesService ruleService;
    private final RecommendationRepository recommendations;
    private final Clock clock;

    public AnalyticsService(ClanDataRepository clans, PlayerRepository players, WarRepository wars,
            WarParticipantRepository participants, MembershipHistoryRepository snapshots,
            MembershipEventRepository events, ClanHistoryRepository clanSnapshots, ClashRoyaleProperties api, RecommendationRulesService ruleService, RecommendationRepository recommendations, Clock clock) {
        this.clans = clans; this.players = players; this.wars = wars; this.participants = participants;
        this.snapshots = snapshots; this.events = events; this.api = api; this.ruleService = ruleService; this.recommendations = recommendations; this.clock = clock;
        this.clanSnapshots = clanSnapshots;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AnalyticsDtos.Report report(LocalDate requestedWeek) {
        return calculate(requestedWeek, ruleService.current());
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, propagation = Propagation.REQUIRES_NEW)
    public AnalyticsDtos.Report reportWithRules(LocalDate requestedWeek, RuleVersion version) {
        return calculate(requestedWeek, version);
    }

    private AnalyticsDtos.Report calculate(LocalDate requestedWeek, RuleVersion version) {
        var rules = version.settings().analytics();
        Instant now = clock.instant();
        LocalDate currentWeek = DonationCalculator.week(now, rules.timezone());
        LocalDate week = requestedWeek == null ? currentWeek : requestedWeek;
        if (week.getDayOfWeek() != DayOfWeek.MONDAY || week.isAfter(currentWeek)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "week deve ser uma segunda-feira não futura, no formato YYYY-MM-DD.");
        }
        var clan = clans.findByTag(api.clanTag()).orElse(null);
        if (clan == null) return enrich(new AnalyticsEngine(rules).analyze(new AnalyticsEngine.Data(List.of(), List.of(), List.of(), List.of(), List.of()), now, week), version);
        var warData = wars.findByClanIdOrderByPublishedAtDescIdDesc(clan.getId());
        var participantData = warData.isEmpty() ? List.<com.frotafantasma.analytics.entity.WarParticipant>of()
                : participants.findByWarIdIn(warData.stream().map(w -> w.getId()).toList());
        var snapshotData = snapshots.findByClanIdOrderByCapturedAtAscIdAsc(clan.getId());
        var eventData = events.findByClanIdOrderByObservedAtAscIdAsc(clan.getId());
        Set<Long> playerIds = new HashSet<>();
        snapshotData.forEach(s -> playerIds.add(s.getPlayerId()));
        participantData.forEach(p -> playerIds.add(p.getPlayerId()));
        eventData.forEach(e -> playerIds.add(e.getPlayerId()));
        List<Player> playerData = players.findAllById(playerIds);
        var clanData = clanSnapshots.findByClanIdOrderByCapturedAtDescIdDesc(clan.getId(), org.springframework.data.domain.Pageable.unpaged()).getContent();
        return enrich(new AnalyticsEngine(rules, version.settings().immortalAllowPartial()).analyze(new AnalyticsEngine.Data(playerData, warData, participantData, snapshotData, eventData, clanData), now, week), version);
    }
    private AnalyticsDtos.Report enrich(AnalyticsDtos.Report report, RuleVersion version) {
        Instant end = report.week().plusWeeks(1).atStartOfDay(report.rules().timezone()).toInstant();
        Instant cutoff = report.generatedAt().isBefore(end) ? report.generatedAt() : end;
        var alerts = recommendations.alerts(api.clanTag(), cutoff.minus(Duration.ofDays(version.settings().recentAlertDays())), cutoff);
        return RecommendationPolicy.apply(report, version, alerts, cutoff);
    }
}
