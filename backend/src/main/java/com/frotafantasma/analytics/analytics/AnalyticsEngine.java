package com.frotafantasma.analytics.analytics;

import static com.frotafantasma.analytics.analytics.AnalyticsDtos.*;
import com.frotafantasma.analytics.entity.*;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.DayOfWeek;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Pure analysis: no remote calls and no promotion, punishment or membership mutations. */
public final class AnalyticsEngine {
    public record Data(List<Player> players, List<War> wars, List<WarParticipant> participants,
                       List<ClanMembershipSnapshot> snapshots, List<MembershipEvent> events, List<ClanHistory> clanSnapshots) {
        public Data(List<Player> players, List<War> wars, List<WarParticipant> participants,
                    List<ClanMembershipSnapshot> snapshots, List<MembershipEvent> events) {
            this(players, wars, participants, snapshots, events, List.of());
        }
    }
    private record WarRanking(Map<Long, Integer> positions, List<String> evidence) {}
    private final AnalyticsProperties rules;
    private final boolean immortalAllowPartial;
    public AnalyticsEngine(AnalyticsProperties rules) { this(rules, false); }
    public AnalyticsEngine(AnalyticsProperties rules, boolean immortalAllowPartial) { this.rules = rules; this.immortalAllowPartial = immortalAllowPartial; }

    public Report analyze(Data data, Instant now, LocalDate week) {
        Instant end = week.plusWeeks(1).atStartOfDay(rules.timezone()).toInstant();
        Instant cutoff = now.isBefore(end) ? now : end;
        List<War> wars = data.wars().stream().filter(w -> w.getPublishedAt().isBefore(cutoff))
                .sorted(Comparator.comparing(War::getPublishedAt).reversed().thenComparing(War::getId, Comparator.reverseOrder())).toList();
        var ranks = new HashMap<Long, WarRanking>();
        data.participants().stream().filter(p -> valid(p.getContribution()))
                .collect(Collectors.groupingBy(WarParticipant::getWarId)).forEach((warId, rows) -> {
                    var sorted = rows.stream().sorted(Comparator.comparing(WarParticipant::getContribution).reversed()).toList();
                    var positions = new HashMap<Long, Integer>();
                    int rank = 0;
                    for (int i = 0; i < sorted.size(); i++) {
                        if (i == 0 || !sorted.get(i).getContribution().equals(sorted.get(i - 1).getContribution())) rank = i + 1;
                        positions.put(sorted.get(i).getPlayerId(), rank);
                    }
                    ranks.put(warId, new WarRanking(positions, evidence(sorted)));
                });
        var participants = data.participants().stream().collect(Collectors.groupingBy(WarParticipant::getPlayerId));
        var snapshots = data.snapshots().stream().filter(s -> s.getCapturedAt().isBefore(cutoff))
                .collect(Collectors.groupingBy(ClanMembershipSnapshot::getPlayerId));
        var events = data.events().stream().filter(e -> e.getObservedAt().isBefore(cutoff))
                .collect(Collectors.groupingBy(MembershipEvent::getPlayerId));
        Set<Long> knownWarIds = wars.stream().map(War::getId).collect(Collectors.toSet());
        var players = data.players().stream().filter(p -> snapshots.containsKey(p.getId()) || events.containsKey(p.getId())
                        || participants.getOrDefault(p.getId(), List.of()).stream().anyMatch(w -> knownWarIds.contains(w.getWarId())))
                .sorted(Comparator.comparing(Player::getPlayerTag))
                .map(p -> player(p, wars, participants.getOrDefault(p.getId(), List.of()),
                        snapshots.getOrDefault(p.getId(), List.of()), events.getOrDefault(p.getId(), List.of()), cutoff, week, ranks)).toList();
        Period calendar = calendar(week);
        var roster = data.clanSnapshots().stream().filter(c -> c.getCapturedAt().isBefore(cutoff))
                .max(Comparator.comparing(ClanHistory::getCapturedAt).thenComparing(ClanHistory::getId)).orElse(null);
        var clan = clan(players, wars, data.events(), roster, calendar, cutoff);
        var evolution = evolution(data, cutoff);
        return new Report(now, week, AnalyticsProperties.VERSION, rules, players, clan, highlights(players, wars, week, calendar), evolution);
    }

    private PlayerStats player(Player player, List<War> wars, List<WarParticipant> participants,
                               List<ClanMembershipSnapshot> snapshots, List<MembershipEvent> events,
                               Instant cutoff, LocalDate week, Map<Long, WarRanking> ranks) {
        var byWar = participants.stream().collect(Collectors.toMap(WarParticipant::getWarId, Function.identity()));
        var latest = snapshots.stream().max(Comparator.comparing(ClanMembershipSnapshot::getCapturedAt)
                .thenComparing(ClanMembershipSnapshot::getId)).orElse(null);
        var lastEvent = events.stream().max(Comparator.comparing(MembershipEvent::getObservedAt).thenComparing(MembershipEvent::getId)).orElse(null);
        boolean active = latest != null && lastEvent != null && !"LEAVE".equals(lastEvent.getType());
        var joined = events.stream().filter(e -> "JOIN".equals(e.getType()) || "BASELINE".equals(e.getType()))
                .max(Comparator.comparing(MembershipEvent::getObservedAt).thenComparing(MembershipEvent::getId)).orElse(null);
        boolean fresh = latest != null && !latest.getCapturedAt().isBefore(cutoff.minus(rules.freshness()));
        List<War> recent = wars.stream().limit(4).toList();
        var history = wars.stream().map(w -> war(w, byWar.get(w.getId()), ranks.get(w.getId()))).toList();
        List<WarParticipant> allRows = wars.stream().map(w -> byWar.get(w.getId())).filter(Objects::nonNull).toList();
        List<WarParticipant> rows = recent.stream().map(w -> byWar.get(w.getId())).filter(Objects::nonNull).toList();
        Period period = warPeriod(recent);
        var evidence = evidence(rows);
        int pointCount = (int) rows.stream().filter(p -> valid(p.getContribution())).count();
        int deckCount = (int) rows.stream().filter(p -> valid(p.getDecksUsed())).count();
        int participated = (int) rows.stream().filter(this::participated).count();
        int zero = (int) rows.stream().filter(this::zero).count();
        int full = (int) rows.stream().filter(p -> Objects.equals(p.getDecksUsed(), 16)).count();
        int partial = (int) rows.stream().filter(p -> p.getDecksUsed() != null && p.getDecksUsed() > 0 && p.getDecksUsed() < 16).count();
        int unknown = recent.size() - participated - zero;
        boolean insufficient = recent.size() < rules.minimumWars() || pointCount < rules.minimumWars() || deckCount < rules.minimumWars();
        boolean incomplete = rows.size() < recent.size() || pointCount < recent.size() || deckCount < recent.size();
        var metrics = new LinkedHashMap<String, Metric<?>>();
        long points = rows.stream().filter(p -> valid(p.getContribution())).mapToLong(WarParticipant::getContribution).sum();
        long decks = rows.stream().filter(p -> valid(p.getDecksUsed())).mapToLong(WarParticipant::getDecksUsed).sum();
        Double average = pointCount == 0 ? null : (double) points / pointCount;
        boolean anomaly = rows.stream().anyMatch(p -> p.getDecksUsed() != null && p.getDecksUsed() > 16);
        metrics.put("recentPoints", metric(pointCount == 0 ? null : points, Origin.DERIVED, period, rows.size(), insufficient, incomplete, evidence, "Soma de fame conhecido nas últimas quatro guerras do clã; ausência de registro não vira zero."));
        metrics.put("recentAverage", metric(average, Origin.DERIVED, period, pointCount, insufficient, incomplete, evidence, "Soma de fame / número de pontuações conhecidas (inclui zeros explícitos); não seleciona somente as guerras em que jogou."));
        metrics.put("recentDecks", metric(deckCount == 0 ? null : decks, Origin.DERIVED, period, deckCount, insufficient, incomplete, evidence, "Soma dos decksUsed conhecidos nas últimas quatro guerras do clã."));
        metrics.put("standardDeckTargetPercent", metric(deckCount == 0 || anomaly ? null : decks * 100.0 / (16L * deckCount), Origin.DERIVED,
                period, deckCount, insufficient, incomplete || anomaly, evidence,
                "Percentual do padrão de 16 decks por guerra COM decks conhecidos; não é disponibilidade individual nem mede culpa por decks faltantes. Valores acima de 16 tornam a comparação indisponível."));
        metrics.put("deckUtilizationPercent", unavailable(period, recent.size(), "A API não fornece quantos decks este jogador pôde usar, elegibilidade diária, bloqueio de 50 participantes ou horário exato de ingresso. Não presumimos 64 decks disponíveis."));
        metrics.put("fullDeckUtilizationWars", unavailable(period, recent.size(), "Sem disponibilidade individual confirmada não é possível contar guerras com 100% dos decks elegíveis. completeStandardWars conta apenas o padrão observado de 16."));
        metrics.put("completeStandardWars", metric(deckCount == 0 ? null : full, Origin.DERIVED, period, deckCount, insufficient, incomplete, evidence, "Conta guerras com exatamente 16 decksUsed, conforme padrão de quatro dias e quatro decks. Não comprova disponibilidade em guerras parciais."));
        metrics.put("partialStandardWars", metric(deckCount == 0 ? null : partial, Origin.DERIVED, period, deckCount, insufficient, incomplete, evidence, "Conta 1 a 15 decks observados; participação parcial em relação ao padrão, sem assumir elegibilidade integral."));
        metrics.put("zeroParticipationWars", metric(rows.isEmpty() ? null : zero, Origin.DERIVED, period, rows.size(), insufficient, incomplete, evidence, "Conta apenas fame=0 E decksUsed=0 explícitos; jogador ausente do payload permanece desconhecido."));
        metrics.put("recentParticipation", metric(rows.isEmpty() ? null : participated + "/" + recent.size(), Origin.DERIVED, period, recent.size(), insufficient, incomplete || unknown > 0, evidence, "Numerador: fame>0 ou decksUsed>0; denominador: últimas guerras disponíveis do clã (máximo quatro), não semanas inventadas. Desconhecidas=" + unknown));
        metrics.put("analyzedWars", metric(recent.size(), Origin.DERIVED, period, recent.size(), insufficient, false, recent.stream().map(w -> "war:" + w.getId()).toList(), "Guerras únicas por temporada/seção; cada guerra é um período semanal oficial, sem fabricar datas de início/fim."));
        Period lifetime = warPeriod(wars);
        var scores = allRows.stream().filter(p -> valid(p.getContribution())).mapToInt(WarParticipant::getContribution).summaryStatistics();
        var allEvidence = evidence(allRows);
        metrics.put("historicalAverage", metric(scores.getCount() == 0 ? null : scores.getAverage(), Origin.DERIVED, lifetime, (int) scores.getCount(), scores.getCount() < rules.minimumWars(), allRows.size() < wars.size(), allEvidence, "Média de todo fame conhecido no histórico disponível; não representa a carreira completa."));
        metrics.put("warsParticipated", metric(allRows.isEmpty() ? null : allRows.stream().filter(this::participated).count(), Origin.DERIVED, lifetime, allRows.size(), allRows.size() < rules.minimumWars(), allRows.size() < wars.size(), allEvidence, "Total de guerras disponíveis com fame>0 ou decksUsed>0."));
        metrics.put("bestPoints", metric(scores.getCount() == 0 ? null : scores.getMax(), Origin.DERIVED, lifetime, (int) scores.getCount(), scores.getCount() == 0, false, allEvidence, "Máximo fame observado."));
        metrics.put("worstPoints", metric(scores.getCount() == 0 ? null : scores.getMin(), Origin.DERIVED, lifetime, (int) scores.getCount(), scores.getCount() == 0, false, allEvidence, "Mínimo fame observado; inclui zero explícito."));
        Double trend = null;
        if (recent.size() == 4 && pointCount == 4) {
            trend = (byWar.get(recent.get(0).getId()).getContribution() + (double) byWar.get(recent.get(1).getId()).getContribution()) / 2
                    - (byWar.get(recent.get(2).getId()).getContribution() + (double) byWar.get(recent.get(3).getId()).getContribution()) / 2;
        }
        metrics.put("recentTrendPoints", metric(trend, Origin.DERIVED, period, pointCount, trend == null, incomplete, evidence, "Média das duas guerras mais recentes menos média das duas anteriores; exige quatro pontuações conhecidas."));
        Period observed = latest == null ? new Period(null, cutoff, "MEMBER_SNAPSHOT") : new Period(latest.getCapturedAt(), latest.getCapturedAt(), "MEMBER_SNAPSHOT");
        var memberEvidence = latest == null ? List.<String>of() : List.of("membership:" + latest.getId() + "@" + latest.getCapturedAt(),
                "trophies=" + latest.getTrophies(), "api.lastSeen=" + latest.getApiLastSeen(), "donations=" + latest.getDonations());
        metrics.put("trophies", metric(latest == null ? null : latest.getTrophies(), Origin.DIRECT_API, observed, 0, latest == null, !fresh, memberEvidence, "Troféus no último snapshot de membros; não extrapola após a coleta."));
        Instant lastSeen = latest == null ? null : latest.getApiLastSeen();
        boolean activityValid = lastSeen != null && !lastSeen.isAfter(latest.getCapturedAt());
        metrics.put("lastKnownActivity", metric(activityValid ? lastSeen : null, Origin.DIRECT_API, observed, 0, !activityValid, !fresh, memberEvidence, "lastSeen real de /members; distinto de player.lastSeenAt, que é observação local. Timestamp futuro não é usado."));
        Long offline = activityValid ? Duration.between(lastSeen, latest.getCapturedAt()).toDays() : null;
        metrics.put("offlineDays", metric(offline, Origin.DERIVED, observed, 0, offline == null, !fresh, memberEvidence, "Dias completos entre lastSeen da API e captura (não o relógio atual); coleta desatualizada não produz alerta de inatividade."));
        Long tenure = active && joined != null ? Math.max(0, Duration.between(joined.getObservedAt(), latest.getCapturedAt()).toDays()) : null;
        var tenureEvidence = joined == null ? memberEvidence : List.of("membershipEvent:" + joined.getId() + ":" + joined.getType() + "@" + joined.getObservedAt(), "observedThrough=" + latest.getCapturedAt());
        metrics.put("observedTenureDays", metric(tenure, Origin.SNAPSHOT_DERIVED, new Period(joined == null ? null : joined.getObservedAt(), latest == null ? null : latest.getCapturedAt(), "CURRENT_MEMBERSHIP_OBSERVED"), 0,
                tenure == null, true, tenureEvidence, "Tempo aproximado do episódio atual desde último JOIN/BASELINE. BASELINE não é ingresso real; reingresso reinicia a estimativa, e lacunas podem esconder saídas."));
        var donationPeriods = DonationCalculator.calculate(snapshots, events, rules.timezone(), cutoff);
        var donations = donationPeriods.stream().filter(d -> d.week().equals(week)).findFirst().map(DonationPeriod::observedDonations)
                .orElseGet(() -> metric(null, Origin.SNAPSHOT_DERIVED, calendar(week), 0, true, true, List.of(), "Dados insuficientes: são necessários ao menos dois snapshots comparáveis no período."));
        metrics.put("weeklyDonations", donations);
        int below = (int) rows.stream().filter(p -> valid(p.getContribution()) && p.getContribution() < rules.pointsTarget()).count();
        metrics.put("warsBelowTarget", metric(pointCount == 0 ? null : below, Origin.DERIVED, period, pointCount, insufficient, incomplete, evidence, "Pontuações explícitas abaixo da meta configurada de " + rules.pointsTarget() + ". Uma guerra isolada não comprova baixa participação."));
        String participation = insufficient ? "DADOS_INSUFICIENTES" : zero >= 2 ? "AUSENCIA_RECORRENTE_OBSERVADA"
                : participated == recent.size() && full == recent.size() ? "COMPLETA_PADRAO"
                : partial >= 2 || zero > 0 ? "IRREGULAR" : "ADEQUADA";
        var participationMetric = metric(participation, Origin.DERIVED, period, rows.size(), insufficient, incomplete, evidence,
                "Participações=" + participated + ", completas no padrão=" + full + ", parciais=" + partial + ", zeros explícitos=" + zero + ", desconhecidas=" + unknown
                        + ". Sem amostra mínima de " + rules.minimumWars() + " não conclui baixa participação. Disponibilidade individual desconhecida.");
        var reasons = new ArrayList<String>();
        String status = "MANTER";
        if (insufficient) reasons.add("Histórico recente insuficiente para concluir consistência.");
        if (!fresh) reasons.add("Snapshot de membros desatualizado ou ausente.");
        if (!activityValid) reasons.add("Última atividade oficial desconhecida ou inválida.");
        if (anomaly) reasons.add("Decks oficiais excedem o padrão validado; conferir a fonte antes de concluir consistência.");
        if (below >= 2) reasons.add(below + " guerras abaixo da meta " + rules.pointsTarget() + ".");
        if (partial >= 2) reasons.add(partial + " guerras com uso parcial do padrão de decks; elegibilidade individual desconhecida.");
        if (zero >= 2) reasons.add(zero + " guerras com zero participação explícita; verificar ingresso e contexto antes de agir.");
        if (fresh && offline != null && offline >= rules.offlineDays()) reasons.add("lastSeen oficial indica " + offline + " dias offline na captura.");
        else if (fresh && offline != null && offline >= Math.max(1, rules.offlineDays() - 2)) reasons.add("Baixa atividade: lastSeen oficial indica " + offline + " dias offline na captura.");
        if (!reasons.isEmpty()) status = "OBSERVAR";
        // Recurring poor results require sample AND local tenure evidence predating all four windows.
        boolean established = joined != null && !recent.isEmpty()
                && joined.getObservedAt().isBefore(recent.getLast().getPublishedAt().minus(Duration.ofDays(7)));
        if (fresh && ((offline != null && offline >= rules.offlineDays())
                || (!insufficient && established && (zero >= 2 || below >= rules.reviewLowWars())))) status = "REVISAR";
        if (reasons.isEmpty()) reasons.add("Amostra suficiente e participação sem alertas recorrentes nas regras atuais.");
        var decisionEvidence = new ArrayList<>(evidence); decisionEvidence.addAll(memberEvidence); decisionEvidence.addAll(tenureEvidence);
        var decision = metric(active ? status : null, Origin.SNAPSHOT_DERIVED, period, rows.size(), insufficient, incomplete || !fresh,
                decisionEvidence, active ? String.join(" ", reasons) + " Classificação analítica; nenhuma ação automática." : "Jogador fora do elenco observado; não classificar operacionalmente.");
        String context = fresh && offline != null && offline >= rules.offlineDays() ? "ATUALMENTE_INATIVO"
                : joined != null && "JOIN".equals(joined.getType()) && tenure != null && tenure < rules.promotionTenureDays() ? "NOVO"
                : insufficient || tenure == null ? "AMOSTRA_INSUFICIENTE"
                : tenure >= rules.promotionTenureDays() && trend != null && trend < 0 ? "ANTIGO_EM_QUEDA"
                : partial >= 2 || zero > 0 || below >= 2 ? "IRREGULAR"
                : tenure >= rules.promotionTenureDays() ? "ANTIGO_CONSISTENTE" : "POUCO_TEMPO_OBSERVADO";
        var contextMetric = metric(context, Origin.SNAPSHOT_DERIVED, period, rows.size(), insufficient, incomplete || !fresh, decisionEvidence,
                "Contexto usa amostra, tendência em quatro guerras, episódio de associação observado e lastSeen oficial; primeira observação não prova que o jogador é novo.");
        Number donationValue = (Number) donations.value();
        Double targetPercent = (Double) metrics.get("standardDeckTargetPercent").value();
        boolean candidate = active && latest != null && "member".equals(latest.getRole()) && "MANTER".equals(status)
                && !insufficient && participated == recent.size() && average != null && average >= rules.pointsTarget()
                && targetPercent != null && targetPercent >= rules.adequateStandardDeckPercent()
                && tenure != null && tenure >= rules.promotionTenureDays()
                && donationValue != null && donationValue.longValue() >= rules.promotionDonations();
        var promotionEvidence = new ArrayList<>(decisionEvidence); promotionEvidence.addAll(donations.dataUsed());
        var unmet = new ArrayList<String>();
        if (!active) unmet.add("não está no elenco observado");
        if (latest == null || !"member".equals(latest.getRole())) unmet.add("cargo não elegível ou desconhecido");
        if (insufficient) unmet.add("amostra insuficiente");
        if (participated != recent.size()) unmet.add("participação positiva em " + participated + "/" + recent.size());
        if (average == null || average < rules.pointsTarget()) unmet.add("média=" + average);
        if (targetPercent == null || targetPercent < rules.adequateStandardDeckPercent()) unmet.add("uso do padrão=" + targetPercent);
        if (tenure == null || tenure < rules.promotionTenureDays()) unmet.add("dias observados=" + tenure);
        if (donationValue == null || donationValue.longValue() < rules.promotionDonations()) unmet.add("doações observadas=" + donationValue);
        if (!"MANTER".equals(status)) unmet.add("status=" + status);
        String promotionReason = "Candidato somente se cargo member, participação em todas as guerras da janela, amostra mínima=" + rules.minimumWars()
                + ", média >= " + rules.pointsTarget() + ", uso do padrão de decks >= " + rules.adequateStandardDeckPercent()
                + "%, tempo observado >= " + rules.promotionTenureDays() + " dias, doações observadas >= " + rules.promotionDonations()
                + " e status MANTER (sem alertas atuais na janela). Resultado=" + candidate + ". Critérios pendentes: "
                + (unmet.isEmpty() ? "nenhum" : String.join("; ", unmet)) + ". " + String.join(" ", reasons)
                + " Doações são limite inferior; elegibilidade individual não é presumida. Sugestão de Ancião, nunca promoção automática.";
        return new PlayerStats(player.getPlayerTag(), latest == null ? player.getName() : latest.getName(), latest == null ? null : latest.getRole(), active,
                metrics, history, donationPeriods, participationMetric, contextMetric, decision,
                metric(candidate, Origin.SNAPSHOT_DERIVED, period, rows.size(), insufficient || donationValue == null, incomplete || donations.incompletePeriod() || !fresh, promotionEvidence, promotionReason));
    }

    private WarStats war(War war, WarParticipant row, WarRanking ranking) {
        Integer rank = row == null || ranking == null ? null : ranking.positions().get(row.getPlayerId());
        Period period = new Period(war.getStartDate(), war.getEndDate(), "OFFICIAL_WAR:" + war.getSeasonId() + ":" + war.getSectionIndex() + ";publishedAt=" + war.getPublishedAt());
        var evidence = row == null ? List.of("war:" + war.getId() + ":participant absent") : evidence(List.of(row));
        String state = row == null ? null : zero(row) ? "ZERO_OBSERVADO" : Objects.equals(row.getDecksUsed(), 16) ? "COMPLETA_PADRAO"
                : row.getDecksUsed() != null && row.getDecksUsed() > 0 && row.getDecksUsed() < 16 ? "PARCIAL_PADRAO"
                : participated(row) ? "PARTICIPOU" : null;
        return new WarStats(war.getId(), war.getSeasonId(), war.getSectionIndex(), war.getPublishedAt(),
                metric(row == null || !valid(row.getContribution()) ? null : row.getContribution(), Origin.DIRECT_API, period, row == null ? 0 : 1, row == null, false, evidence, "fame oficial; início/fim não inferidos de createdDate."),
                metric(row == null || !valid(row.getDecksUsed()) ? null : row.getDecksUsed(), Origin.DIRECT_API, period, row == null ? 0 : 1, row == null, false, evidence, "decksUsed oficial; não equivale a decks disponíveis."),
                metric(state, Origin.DERIVED, period, row == null ? 0 : 1, state == null, state == null, evidence, "Registro ausente é desconhecido, nunca ausência ou zero fabricado."),
                metric(rank, Origin.DERIVED, period, rank == null ? 0 : 1, rank == null, false, ranking == null ? evidence : ranking.evidence(),
                        "Posição por fame entre todos os participantes registrados do clã nesta guerra, incluindo ex-membros. Empates compartilham posição (1, 1, 3); pontuação desconhecida não é classificada."));
    }

    private Map<String, Metric<?>> clan(List<PlayerStats> players, List<War> wars, List<MembershipEvent> events, ClanHistory roster, Period calendar, Instant cutoff) {
        var current = players.stream().filter(PlayerStats::currentMember).toList();
        var result = new LinkedHashMap<String, Metric<?>>();
        var rosterEvidence = current.stream().map(p -> p.tag() + ":" + p.metrics().get("trophies").dataUsed()).toList();
        boolean stale = current.stream().anyMatch(p -> p.metrics().get("trophies").incompletePeriod());
        boolean rosterKnown = roster != null || players.stream().anyMatch(p -> !p.metrics().get("trophies").dataUsed().isEmpty());
        result.put("currentMembers", metric(roster == null ? null : roster.getMemberCount(), Origin.DIRECT_API,
                roster == null ? calendar : new Period(roster.getCapturedAt(), roster.getCapturedAt(), "CLAN_SNAPSHOT"), 0, roster == null,
                roster == null || roster.getCapturedAt().isBefore(cutoff.minus(rules.freshness())),
                roster == null ? rosterEvidence : List.of("clanHistory:" + roster.getId() + ":members=" + roster.getMemberCount()), "Quantidade oficial de membros na última captura do clã; não número ao vivo."));
        for (String type : List.of("JOIN", "LEAVE")) {
            var matching = events.stream().filter(e -> type.equals(e.getType()) && !e.getObservedAt().isBefore(calendar.from()) && e.getObservedAt().isBefore(cutoff)).toList();
            result.put(type.equals("JOIN") ? "newMembers" : "departures", metric(rosterKnown ? matching.size() : null, Origin.SNAPSHOT_DERIVED, calendar, 0, !rosterKnown, true,
                    matching.stream().map(e -> "membershipEvent:" + e.getId()).toList(), "Eventos " + type + " observados no período; BASELINE não conta como entrada. Lacunas podem ocultar eventos."));
        }
        result.put("observedWeeklyDonations", aggregate(current, "weeklyDonations", calendar, "Soma de limites inferiores observados dos membros atuais; não substitui total exato do clã."));
        Period window = warPeriod(wars.stream().limit(4).toList());
        result.put("totalRecentDecks", aggregate(current, "recentDecks", window, "Decks conhecidos dos membros atuais nas últimas quatro guerras; ex-membros excluídos desta visão."));
        var scoreValues = current.stream().flatMap(p -> p.wars().stream().limit(4)).map(w -> w.points().value()).filter(Objects::nonNull).toList();
        result.put("averageRecentPoints", metric(scoreValues.isEmpty() ? null : scoreValues.stream().mapToInt(Integer::intValue).average().orElseThrow(), Origin.DERIVED,
                window, Math.min(4, wars.size()), wars.size() < rules.minimumWars() || scoreValues.isEmpty(), current.stream().anyMatch(p -> p.metrics().get("recentAverage").incompletePeriod()),
                current.stream().flatMap(p -> p.metrics().get("recentAverage").dataUsed().stream()).toList(), "Média ponderada por jogador/guerra com pontuação conhecida, incluindo zeros explícitos."));
        var known = current.stream().flatMap(p -> p.wars().stream().limit(4)).filter(w -> w.points().value() != null && w.decksUsed().value() != null).toList();
        long participated = known.stream().filter(w -> w.points().value() > 0 || w.decksUsed().value() > 0).count();
        var warEvidence = known.stream().flatMap(w -> w.decksUsed().dataUsed().stream()).toList();
        result.put("participationPercent", metric(known.isEmpty() ? null : participated * 100.0 / known.size(), Origin.DERIVED, window,
                Math.min(4, wars.size()), wars.size() < rules.minimumWars() || known.isEmpty(), known.size() < current.size() * Math.min(4, wars.size()), warEvidence, "Participações positivas / pares jogador-guerra conhecidos; desconhecidos excluídos e cobertura explicitada."));
        boolean anomaly = known.stream().anyMatch(w -> w.decksUsed().value() > 16);
        result.put("standardDeckTargetPercent", metric(known.isEmpty() || anomaly ? null : known.stream().mapToLong(w -> w.decksUsed().value()).sum() * 100.0 / (16L * known.size()),
                Origin.DERIVED, window, Math.min(4, wars.size()), known.isEmpty() || wars.size() < rules.minimumWars(), anomaly || known.size() < current.size() * Math.min(4, wars.size()), warEvidence, "Uso do padrão de 16 por par conhecido; não disponibilidade efetiva do clã."));
        result.put("deckUtilizationPercent", unavailable(window, Math.min(4, wars.size()), "Disponibilidade individual e bloqueios diários não fornecidos pela API."));
        var below = current.stream().filter(p -> !p.wars().isEmpty() && p.wars().getFirst().points().value() != null && p.wars().getFirst().points().value() < rules.pointsTarget()).toList();
        long latestKnown = current.stream().filter(p -> !p.wars().isEmpty() && p.wars().getFirst().points().value() != null).count();
        result.put("membersBelowTarget", metric(latestKnown == 0 ? null : below.size(), Origin.DERIVED, warPeriod(wars.stream().limit(1).toList()), Math.min(1, wars.size()), wars.isEmpty(), latestKnown < current.size(),
                below.stream().flatMap(p -> p.wars().getFirst().points().dataUsed().stream()).toList(), "Membros atuais abaixo de " + rules.pointsTarget() + " na última guerra publicada; pontuação ausente não é zero."));
        var offline = current.stream().filter(p -> !p.metrics().get("offlineDays").incompletePeriod() && number(p, "offlineDays", -1) >= rules.offlineDays()).toList();
        long activityKnown = current.stream().filter(p -> p.metrics().get("offlineDays").value() != null && !p.metrics().get("offlineDays").incompletePeriod()).count();
        result.put("membersOffline", metric(activityKnown == 0 ? null : offline.size(), Origin.DERIVED, calendar, 0, activityKnown == 0, activityKnown < current.size(),
                offline.stream().flatMap(p -> p.metrics().get("offlineDays").dataUsed().stream()).toList(), "Conta somente lastSeen oficial com captura recente e pelo menos " + rules.offlineDays() + " dias offline."));
        for (String status : List.of("OBSERVAR", "REVISAR")) {
            var matching = current.stream().filter(p -> status.equals(p.operationalStatus().value())).toList();
            result.put(status.equals("OBSERVAR") ? "membersToObserve" : "membersToReview", metric(rosterKnown ? matching.size() : null, Origin.SNAPSHOT_DERIVED, window, Math.min(4, wars.size()), wars.size() < rules.minimumWars(), stale,
                    matching.stream().map(p -> p.tag() + ":" + p.operationalStatus().reason()).toList(), "Classificação analítica " + status + "; sem ações automáticas."));
        }
        return result;
    }

    private List<Highlight> highlights(List<PlayerStats> players, List<War> wars, LocalDate week, Period period) {
        Set<String> assigned = new HashSet<>();
        var current = players.stream().filter(PlayerStats::currentMember).toList();
        var awards = new ArrayList<Highlight>();
        boolean warPublishedThisWeek = !wars.isEmpty() && DonationCalculator.week(wars.getFirst().getPublishedAt(), rules.timezone()).equals(week);
        var captains = current.stream().filter(p -> warPublishedThisWeek && !p.wars().isEmpty() && p.wars().getFirst().points().value() != null && p.wars().getFirst().points().value() > 0)
                .sorted(Comparator.<PlayerStats>comparingInt(p -> p.wars().getFirst().points().value()).reversed().thenComparing(PlayerStats::tag)).toList();
        award(awards, assigned, "CAPITAO_DA_GUERRA", captains, period, p -> p.wars().getFirst().points(),
                p -> "Maior pontuação da última guerra publicada nesta semana; pontos=" + p.wars().getFirst().points().value() + ", decks=" + p.wars().getFirst().decksUsed().value());
        var guardians = current.stream().filter(p -> number(p, "weeklyDonations", 0) > 0)
                .sorted(Comparator.<PlayerStats>comparingDouble(p -> number(p, "weeklyDonations", 0)).reversed().thenComparing(PlayerStats::tag)).toList();
        award(awards, assigned, "GUARDIAO_DA_FROTA", guardians, period, p -> p.metrics().get("weeklyDonations"),
                p -> "Maior limite inferior de doações observado no período; título provisório porque há lacunas de coleta.");
        var immortals = current.stream().filter(p -> number(p, "analyzedWars", 0) >= rules.immortalMinimumWars()
                        && (immortalAllowPartial ? p.wars().stream().limit(4).filter(w -> w.decksUsed().value()!=null && w.decksUsed().value()>0).count() >= rules.immortalMinimumWars()
                            : number(p, "completeStandardWars", 0) >= rules.immortalMinimumWars())
                        && p.wars().stream().limit(4).allMatch(w -> w.points().value() != null && w.decksUsed().value() != null)
                        && number(p, "zeroParticipationWars", 0) == 0)
                .sorted(Comparator.<PlayerStats>comparingDouble(p -> number(p, "completeStandardWars", 0)).reversed()
                        .thenComparing(Comparator.<PlayerStats>comparingDouble(p -> number(p, "recentDecks", 0)).reversed())
                        .thenComparing(Comparator.<PlayerStats>comparingDouble(p -> number(p, "recentAverage", 0)).reversed())
                        .thenComparing(Comparator.<PlayerStats>comparingDouble(p -> number(p, "weeklyDonations", -1)).reversed())
                        .thenComparing(PlayerStats::tag)).toList();
        award(awards, assigned, "ALMA_IMORTAL", immortals, period, p -> p.metrics().get("recentAverage"),
                p -> "Ordem: guerras com 16 decks, total de decks, média de pontos, doações observadas, tag. Exige " + rules.immortalMinimumWars() + (immortalAllowPartial ? " guerras com participação positiva; parciais habilitadas nesta versão. " : " guerras completas no padrão; ")+"desconhecidos não elegíveis. Doações ausentes não viram zero.");
        return List.copyOf(awards);
    }

    private void award(List<Highlight> results, Set<String> assigned, String title, List<PlayerStats> candidates, Period period,
                       Function<PlayerStats, Metric<?>> metric, Function<PlayerStats, String> reason) {
        var winner = candidates.stream().filter(p -> !assigned.contains(p.tag())).findFirst().orElse(null);
        if (winner == null) {
            results.add(new Highlight(title, null, metric(null, Origin.DERIVED, period, 0, true, true, List.of(), "Dados insuficientes ou nenhum candidato elegível ainda sem título nesta semana.")));
        } else {
            assigned.add(winner.tag());
            Metric<?> source = metric.apply(winner);
            var used = new ArrayList<>(source.dataUsed());
            if ("ALMA_IMORTAL".equals(title)) {
                used.addAll(winner.metrics().get("recentDecks").dataUsed());
                used.addAll(winner.metrics().get("weeklyDonations").dataUsed());
            }
            used.add("rankedCandidates=" + candidates.stream().map(PlayerStats::tag).toList());
            Origin origin = "CAPITAO_DA_GUERRA".equals(title) ? Origin.DERIVED : Origin.SNAPSHOT_DERIVED;
            results.add(new Highlight(title, winner.tag(), metric(source.value(), origin, period, source.warsConsidered(), "ALMA_IMORTAL".equals(title) ? source.warsConsidered() < rules.immortalMinimumWars() : source.insufficientSample(), true,
                    used, reason.apply(winner) + " Alocação provisória exclusiva por semana: Capitão → Guardião → Alma; empate final por tag. Candidato já premiado é pulado.")));
        }
    }

    private List<WeekEvolution> evolution(Data data, Instant cutoff) {
        var weeks = new TreeSet<LocalDate>();
        data.wars().stream().filter(w -> w.getPublishedAt().isBefore(cutoff)).forEach(w -> weeks.add(DonationCalculator.week(w.getPublishedAt(), rules.timezone())));
        data.snapshots().stream().filter(s -> s.getCapturedAt().isBefore(cutoff)).forEach(s -> weeks.add(DonationCalculator.week(s.getCapturedAt(), rules.timezone())));
        var donations = new HashMap<LocalDate, List<DonationPeriod>>();
        data.snapshots().stream().collect(Collectors.groupingBy(ClanMembershipSnapshot::getPlayerId)).forEach((id, snapshots) ->
                DonationCalculator.calculate(snapshots, data.events().stream().filter(e -> e.getPlayerId().equals(id)).toList(), rules.timezone(), cutoff)
                        .forEach(d -> donations.computeIfAbsent(d.week(), k -> new ArrayList<>()).add(d)));
        var result = new ArrayList<WeekEvolution>();
        Double previousAverage = null;
        LocalDate previousWeek = null;
        for (LocalDate week : weeks) {
            var period = calendar(week);
            var wars = data.wars().stream().filter(w -> !w.getPublishedAt().isBefore(period.from()) && w.getPublishedAt().isBefore(period.to()) && w.getPublishedAt().isBefore(cutoff)).toList();
            Set<Long> ids = wars.stream().map(War::getId).collect(Collectors.toSet());
            var rows = data.participants().stream().filter(p -> ids.contains(p.getWarId())).toList();
            var scores = rows.stream().filter(p -> valid(p.getContribution())).mapToInt(WarParticipant::getContribution).summaryStatistics();
            Double avg = scores.getCount() == 0 ? null : scores.getAverage();
            var metrics = new LinkedHashMap<String, Metric<?>>();
            metrics.put("averagePoints", metric(avg, Origin.DERIVED, period, wars.size(), scores.getCount() == 0, scores.getCount() < rows.size(), evidence(rows), "Média de participantes registrados nas guerras publicadas na semana; elenco pode variar entre semanas."));
            var deckRows = rows.stream().filter(p -> valid(p.getDecksUsed())).toList();
            metrics.put("totalDecks", metric(deckRows.isEmpty() ? null : deckRows.stream().mapToLong(WarParticipant::getDecksUsed).sum(), Origin.DERIVED, period, wars.size(), deckRows.isEmpty(), deckRows.size() < rows.size(), evidence(rows), "Decks das guerras publicadas na semana, incluindo ex-membros."));
            var known = rows.stream().filter(p -> valid(p.getContribution()) && valid(p.getDecksUsed())).toList();
            metrics.put("participationPercent", metric(known.isEmpty() ? null : known.stream().filter(this::participated).count() * 100.0 / known.size(),
                    Origin.DERIVED, period, wars.size(), known.isEmpty(), known.size() < rows.size(), evidence(rows), "Participações positivas / registros com pontos e decks conhecidos; não presume elenco de semanas passadas."));
            metrics.put("standardDeckTargetPercent", metric(deckRows.isEmpty() || deckRows.stream().anyMatch(p -> p.getDecksUsed() > 16) ? null
                            : deckRows.stream().mapToLong(WarParticipant::getDecksUsed).sum() * 100.0 / (16L * deckRows.size()),
                    Origin.DERIVED, period, wars.size(), deckRows.isEmpty(), deckRows.size() < rows.size(), evidence(rows), "Percentual do padrão de 16 decks por participante registrado, não disponibilidade individual."));
            var donationRows = donations.getOrDefault(week, List.of());
            var donationKnown = donationRows.stream().filter(d -> d.observedDonations().value() != null).toList();
            metrics.put("observedDonations", metric(donationKnown.isEmpty() ? null : donationKnown.stream().mapToLong(d -> d.observedDonations().value()).sum(),
                    Origin.SNAPSHOT_DERIVED, period, 0, donationKnown.isEmpty(), true,
                    donationRows.stream().flatMap(d -> d.observedDonations().dataUsed().stream()).toList(), "Limite inferior das doações observadas na semana, incluindo ex-membros; cobertura=" + donationKnown.size() + "/" + donationRows.size()));
            for (String type : List.of("JOIN", "LEAVE")) {
                var matching = data.events().stream().filter(e -> type.equals(e.getType()) && !e.getObservedAt().isBefore(period.from())
                        && e.getObservedAt().isBefore(period.to()) && e.getObservedAt().isBefore(cutoff)).toList();
                metrics.put(type.equals("JOIN") ? "newMembers" : "departures", metric(donationRows.isEmpty() ? null : matching.size(), Origin.SNAPSHOT_DERIVED,
                        period, 0, donationRows.isEmpty(), true, matching.stream().map(e -> "membershipEvent:" + e.getId()).toList(), "Eventos " + type + " observados; BASELINE excluída e lacunas sinalizadas."));
            }
            metrics.put("averagePointsChange", metric(avg == null || previousAverage == null || !week.minusWeeks(1).equals(previousWeek) ? null : avg - previousAverage,
                    Origin.DERIVED, new Period(week.minusWeeks(1).atStartOfDay(rules.timezone()).toInstant(), period.to(), "PUBLICATION_WEEKS"), wars.size(), previousAverage == null, true,
                    List.of("previousAverage=" + previousAverage, "currentAverage=" + avg), "Diferença contra semana imediatamente anterior disponível; composição dos participantes pode mudar, não prova melhora individual."));
            result.add(new WeekEvolution(week, metrics));
            previousAverage = avg; previousWeek = week;
        }
        return List.copyOf(result);
    }

    private Metric<?> aggregate(List<PlayerStats> players, String key, Period period, String reason) {
        var values = players.stream().map(p -> p.metrics().get(key)).toList();
        var known = values.stream().filter(m -> m.value() instanceof Number).toList();
        return metric(known.isEmpty() ? null : known.stream().mapToLong(m -> ((Number) m.value()).longValue()).sum(), "recentDecks".equals(key) ? Origin.DERIVED : Origin.SNAPSHOT_DERIVED, period,
                values.stream().mapToInt(Metric::warsConsidered).max().orElse(0), known.isEmpty() || values.stream().anyMatch(Metric::insufficientSample), known.size() < values.size() || values.stream().anyMatch(Metric::incompletePeriod),
                values.stream().flatMap(m -> m.dataUsed().stream()).toList(), reason + " Cobertura=" + known.size() + "/" + values.size() + ".");
    }
    private Period calendar(LocalDate week) { return new Period(week.atStartOfDay(rules.timezone()).toInstant(), week.plusWeeks(1).atStartOfDay(rules.timezone()).toInstant(), "CALENDAR_WEEK_" + rules.timezone()); }
    private Period warPeriod(List<War> wars) { return new Period(wars.isEmpty() ? null : wars.getLast().getPublishedAt(), wars.isEmpty() ? null : wars.getFirst().getPublishedAt(), "PUBLICATION_TIMESTAMPS_NOT_BATTLE_BOUNDARIES"); }
    private List<String> evidence(List<WarParticipant> rows) { return rows.stream().map(p -> "war:" + p.getWarId() + "/participant:" + p.getId() + ":fame=" + p.getContribution() + ",decksUsed=" + p.getDecksUsed()).toList(); }
    private boolean participated(WarParticipant p) { return p.getContribution() != null && p.getContribution() > 0 || p.getDecksUsed() != null && p.getDecksUsed() > 0; }
    private boolean zero(WarParticipant p) { return Objects.equals(p.getContribution(), 0) && Objects.equals(p.getDecksUsed(), 0); }
    private static boolean valid(Integer value) { return value != null && value >= 0; }
    private static double number(PlayerStats p, String key, double fallback) { return p.metrics().get(key).value() instanceof Number n ? n.doubleValue() : fallback; }
    private static Metric<?> unavailable(Period period, int wars, String reason) { return new Metric<>(null, Origin.UNAVAILABLE, Quality.UNAVAILABLE, period, wars, true, true, List.of(), reason); }
    static <T> Metric<T> metric(T value, Origin origin, Period period, int wars, boolean insufficient, boolean incomplete, List<String> evidence, String reason) {
        return new Metric<>(value, origin, value == null || insufficient ? Quality.INSUFFICIENT_DATA : incomplete ? Quality.INCOMPLETE : Quality.AVAILABLE,
                period, wars, insufficient || value == null, incomplete, List.copyOf(evidence), (value == null ? "Dados insuficientes. " : "") + reason);
    }
}
