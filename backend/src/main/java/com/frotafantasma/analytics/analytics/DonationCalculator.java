package com.frotafantasma.analytics.analytics;

import static com.frotafantasma.analytics.analytics.AnalyticsDtos.*;
import com.frotafantasma.analytics.entity.ClanMembershipSnapshot;
import com.frotafantasma.analytics.entity.MembershipEvent;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/** Observed lower bounds, never an invented total for an unobserved part of a week. */
public final class DonationCalculator {
    private DonationCalculator() {}
    public static LocalDate week(Instant date, ZoneId zone) {
        return date.atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
    public static List<DonationPeriod> calculate(List<ClanMembershipSnapshot> snapshots,
                                                 List<MembershipEvent> events, ZoneId zone, Instant cutoff) {
        var grouped = new TreeMap<LocalDate, List<ClanMembershipSnapshot>>();
        snapshots.stream().filter(s -> s.getCapturedAt().isBefore(cutoff)).forEach(s ->
                grouped.computeIfAbsent(week(s.getCapturedAt(), zone), k -> new ArrayList<>()).add(s));
        var result = new ArrayList<DonationPeriod>();
        ClanMembershipSnapshot precedingWeek = null;
        for (var entry : grouped.entrySet()) {
            var rows = entry.getValue().stream().sorted(Comparator.comparing(ClanMembershipSnapshot::getCapturedAt)
                    .thenComparing(ClanMembershipSnapshot::getId)).toList();
            long total = 0; int comparisons = 0, resets = 0;
            var evidence = new ArrayList<String>();
            var first = rows.getFirst();
            if (precedingWeek != null && valid(precedingWeek.getDonations()) && valid(first.getDonations())
                    && first.getDonations() < precedingWeek.getDonations()
                    && sameEpisode(events, precedingWeek.getCapturedAt(), first.getCapturedAt())) {
                resets++;
                evidence.add("boundarySnapshot:" + precedingWeek.getId() + ":donations=" + precedingWeek.getDonations() + "; reset observado, sem creditar diferença entre semanas");
            }
            ClanMembershipSnapshot previous = null;
            for (var row : rows) {
                evidence.add("membership:" + row.getId() + "@" + row.getCapturedAt() + ":donations=" + row.getDonations());
                if (previous != null && valid(previous.getDonations()) && valid(row.getDonations())) {
                    Instant from = previous.getCapturedAt();
                    if (sameEpisode(events, from, row.getCapturedAt()) && row.getCapturedAt().isAfter(from)) {
                        int delta = row.getDonations() - previous.getDonations();
                        total += delta >= 0 ? delta : row.getDonations();
                        if (delta < 0) resets++;
                        comparisons++;
                    }
                }
                previous = row;
            }
            Instant start = entry.getKey().atStartOfDay(zone).toInstant();
            Instant end = entry.getKey().plusWeeks(1).atStartOfDay(zone).toInstant();
            String reason = "Limite inferior observado: soma de diferenças positivas; queda do contador inicia novo segmento e soma o valor pós-reset. "
                    + "Não soma o primeiro contador nem diferenças entre semanas ou reingressos. "
                    + "Lacunas e resets ocultos impedem garantir o total exato; período incompleto.";
            result.add(new DonationPeriod(entry.getKey(), new Metric<>(comparisons == 0 ? null : total,
                    Origin.SNAPSHOT_DERIVED, comparisons == 0 ? Quality.INSUFFICIENT_DATA : Quality.INCOMPLETE,
                    new Period(start, end, "CALENDAR_WEEK_" + zone), 0, comparisons == 0, true, evidence, reason), rows.size(), resets));
            precedingWeek = rows.getLast();
        }
        return List.copyOf(result);
    }
    private static boolean valid(Integer value) { return value != null && value >= 0; }
    private static boolean sameEpisode(List<MembershipEvent> events, Instant from, Instant to) {
        return events.stream().noneMatch(e -> ("JOIN".equals(e.getType()) || "LEAVE".equals(e.getType()))
                && e.getObservedAt().isAfter(from) && !e.getObservedAt().isAfter(to));
    }
}
