package com.frotafantasma.analytics.legendary;

import static com.frotafantasma.analytics.legendary.LegendaryDtos.*;
import java.util.*;

/** Pure weekly rules: independent from the allocation of the three traditional titles. */
public final class LegendaryCalculator {
    private LegendaryCalculator() {}
    public static Candidate candidate(Snapshot now, Snapshot previous, Integer highWater,
                                      WarScore war, String clanTag) {
        List<String> reasons = new ArrayList<>();
        Integer oldRecord = previous == null ? null : previous.recordTrophies();
        // highWater prevents recovery from ever being counted after a missing week.
        if (oldRecord != null && highWater != null) oldRecord = Math.max(oldRecord, highWater);
        Integer improvement = now.recordTrophies() == null || oldRecord == null ? null : now.recordTrophies() - oldRecord;
        Integer net = now.trophies() == null || previous == null || previous.trophies() == null
                ? null : now.trophies() - previous.trophies();
        if (now.capturedAt() == null || now.recordTrophies() == null) reasons.add("Coleta de troféus ausente ou inválida" + (now.errorCode() == null ? "" : " (" + now.errorCode() + ")"));
        if (oldRecord == null) reasons.add("Sem base válida na semana anterior para esta origem e versão de recorde");
        if (!now.fullCycle() || (now.capturedAt() != null && !clanTag.equals(now.profileClanTag()))) reasons.add("Participação no ciclo completo não comprovada pelo histórico de membros");
        if ("leader".equals(now.role())) reasons.add("Líder do clã não concorre");
        if (war == null || war.medals() == null || war.decks() == null) reasons.add("Dados da guerra semanal ausentes");
        else {
            if (war.medals() < 1600) reasons.add("Menos de 1.600 medalhas na guerra");
            if (war.decks() != 16) reasons.add("É necessário utilizar os 16 decks");
        }
        if (improvement != null && improvement < 0) reasons.add("Recorde incompatível com o histórico; revisar a versão da Ladder");
        else if (improvement != null && improvement == 0) reasons.add("Sem superação positiva do recorde");
        return new Candidate(null, now.tag(), now.name(), previous == null ? null : previous.trophies(), now.trophies(),
                net, oldRecord, now.recordTrophies(), improvement, war == null ? null : war.medals(),
                war == null ? null : war.decks(), reasons.isEmpty(), List.copyOf(reasons), now.capturedAt(), now.apiBestTrophies());
    }
    public static List<Candidate> rank(List<Candidate> candidates) {
        var sorted = candidates.stream().sorted(Comparator.comparing(Candidate::eligible).reversed()
                .thenComparing(Candidate::improvement, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Candidate::warMedals, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Candidate::tag)).toList();
        List<Candidate> result = new ArrayList<>();
        int position = 0;
        for (var c : sorted) result.add(new Candidate(c.eligible() ? ++position : null, c.tag(), c.name(), c.previousTrophies(),
                c.trophies(), c.netChange(), c.previousRecord(), c.record(), c.improvement(), c.warMedals(), c.decksUsed(),
                c.eligible(), c.reasons(), c.capturedAt(), c.apiBestTrophies()));
        return List.copyOf(result);
    }
}
