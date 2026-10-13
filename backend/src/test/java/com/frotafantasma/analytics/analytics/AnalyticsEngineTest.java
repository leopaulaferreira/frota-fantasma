package com.frotafantasma.analytics.analytics;

import static org.assertj.core.api.Assertions.*;
import static com.frotafantasma.analytics.analytics.AnalyticsDtos.*;
import com.frotafantasma.analytics.entity.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class AnalyticsEngineTest {
    static final Instant NOW = Instant.parse("2026-10-12T12:00:00Z");
    static final LocalDate WEEK = LocalDate.of(2026, 10, 12);
    static final AnalyticsProperties RULES = new AnalyticsProperties(1600, 5, 4, 4, 3, 80, 28, 100, Duration.ofHours(2), ZoneOffset.UTC);
    final AnalyticsEngine engine = new AnalyticsEngine(RULES);
    final List<Player> players = new ArrayList<>();
    final List<WarParticipant> participants = new ArrayList<>();
    final List<ClanMembershipSnapshot> snapshots = new ArrayList<>();
    final List<MembershipEvent> events = new ArrayList<>();
    final List<War> wars = new ArrayList<>();

    AnalyticsEngineTest() {
        for (int i = 0; i < 4; i++) {
            Instant published = Instant.parse("2026-10-12T09:40:00Z").minus(Duration.ofDays(7L * i));
            wars.add(new War((long) i + 1, 1L, 137, 3 - i, published, null, null, 1, 3000, 10000, 100, published, null));
        }
    }
    void player(long id, int[] scores, int[] decks, int donated) {
        Instant join = Instant.parse("2026-07-01T00:00:00Z");
        players.add(new Player(id, "#P0" + id, "Player " + id, 1L, join, NOW.minusSeconds(1800), true, "member"));
        events.add(new MembershipEvent(id, id, 1L, UUID.randomUUID(), "BASELINE", null, "member", join));
        snapshots.add(snapshot(id * 10, id, NOW.minusSeconds(7200), 0, NOW.minusSeconds(8000)));
        snapshots.add(snapshot(id * 10 + 1, id, NOW.minusSeconds(1800), donated, NOW.minusSeconds(2000)));
        for (int i = 0; i < scores.length; i++) {
            participants.add(new WarParticipant(id * 10 + i, (long) i + 1, id, "Player", scores[i], decks[i], 0, 0, 0, null, null, null, NOW));
        }
    }
    static ClanMembershipSnapshot snapshot(long id, long player, Instant at, Integer donations, Instant lastSeen) {
        var s = new ClanMembershipSnapshot(id, player, 1L, UUID.randomUUID(), "Player " + player, "member", 9000, 1, 1, donations, 0, 15, at);
        s.setApiLastSeen(lastSeen); return s;
    }
    Report report() { return engine.analyze(new AnalyticsEngine.Data(players, wars, participants, snapshots, events), NOW, WEEK); }
    PlayerStats first() { return report().players().getFirst(); }

    @Test void rankingIncluiExMembrosEmpataESaltaPosicoesSemInventarPontuacao() {
        player(1,new int[]{3200},new int[]{16},0);
        player(2,new int[]{3200},new int[]{16},0);
        player(3,new int[]{1800},new int[]{8},0);
        player(4,new int[]{-1},new int[]{0},0);
        events.add(new MembershipEvent(100L,2L,1L,UUID.randomUUID(),"LEAVE",null,null,NOW.minusSeconds(60)));
        var result=report().players();
        assertThat(result).extracting(p -> p.wars().getFirst().rank().value()).containsExactly(1,1,3,null);
        assertThat(result.get(1).currentMember()).isFalse();
        assertThat(result.getFirst().wars().get(1).rank().quality()).isEqualTo(Quality.INSUFFICIENT_DATA);
        assertThat(result.getFirst().wars().getFirst().rank().origin()).isEqualTo(Origin.DERIVED);
    }

    @Test void calculaJanelaDeQuatroGuerrasSemInventarDisponibilidade() {
        player(1, new int[]{3200, 2800, 2400, 2000}, new int[]{16, 16, 12, 8}, 198);
        var p = first();
        assertThat(p.metrics().get("recentAverage").value()).isEqualTo(2600.0);
        assertThat(p.metrics().get("recentPoints").value()).isEqualTo(10400L);
        assertThat(p.metrics().get("recentDecks").value()).isEqualTo(52L);
        assertThat(p.metrics().get("standardDeckTargetPercent").value()).isEqualTo(81.25);
        assertThat(p.metrics().get("deckUtilizationPercent").origin()).isEqualTo(Origin.UNAVAILABLE);
        assertThat(p.metrics().get("deckUtilizationPercent").value()).isNull();
        assertThat(p.metrics().get("completeStandardWars").value()).isEqualTo(2);
        assertThat(p.metrics().get("recentParticipation").value()).isEqualTo("4/4");
        assertThat(p.metrics().get("recentTrendPoints").value()).isEqualTo(800.0);
        assertThat(p.metrics().get("bestPoints").value()).isEqualTo(3200);
        assertThat(p.metrics().get("worstPoints").value()).isEqualTo(2000);
        assertThat(p.metrics().get("weeklyDonations").value()).isEqualTo(198L);
        assertThat(p.metrics().get("weeklyDonations").incompletePeriod()).isTrue();
    }

    @Test void umaGuerraRuimNaoBastaParaClassificarBaixaParticipacao() {
        player(1, new int[]{1000, 3200, 3200, 3200}, new int[]{16, 16, 16, 16}, 198);
        assertThat(first().operationalStatus().value()).isEqualTo("MANTER");
        assertThat(first().participation().value()).isEqualTo("COMPLETA_PADRAO");
    }

    @Test void poucaAmostraEAusenciaDeRegistroNaoViramZerosOuPunicao() {
        player(1, new int[]{1000}, new int[]{4}, 198);
        var p = first();
        assertThat(p.operationalStatus().value()).isEqualTo("OBSERVAR");
        assertThat(p.operationalStatus().insufficientSample()).isTrue();
        assertThat(p.metrics().get("recentParticipation").value()).isEqualTo("1/4");
        assertThat(p.metrics().get("zeroParticipationWars").value()).isEqualTo(0);
        assertThat(p.wars().get(1).points().value()).isNull();
        assertThat(p.metrics().get("recentTrendPoints").value()).isNull();
        assertThat(p.elderCandidate().value()).isFalse();
    }

    @Test void usaQuatroGuerrasDoClaENaoQuatroParticipacoesSelecionadas() {
        player(1, new int[]{3200, 3200, 3200, 3200}, new int[]{16,16,16,16}, 198);
        participants.remove(0);
        wars.add(new War(5L,1L,136,0,NOW.minus(Duration.ofDays(28)),null,null,1,1,1,1,NOW,null));
        participants.add(new WarParticipant(100L,5L,1L,"P",800,4,0,0,0,null,null,null,NOW));
        assertThat(first().metrics().get("recentAverage").value()).isEqualTo(3200.0);
        assertThat(first().metrics().get("recentAverage").insufficientSample()).isTrue();
        assertThat(first().metrics().get("historicalAverage").value()).isEqualTo(2600.0);
    }

    @Test void repeticaoComHistoricoEstabelecidoPodeGerarRevisao() {
        player(1, new int[]{0, 0, 1000, 3200}, new int[]{0,0,8,16}, 198);
        assertThat(first().operationalStatus().value()).isEqualTo("REVISAR");
        assertThat(first().operationalStatus().reason()).contains("3 guerras abaixo", "2 guerras com zero");
        assertThat(first().historicalContext().value()).isEqualTo("ANTIGO_EM_QUEDA");
    }

    @Test void lastSeenOficialPermiteInatividadeMasCapturaAntigaNao() {
        player(1, new int[]{3200,3200,3200,3200}, new int[]{16,16,16,16},198);
        snapshots.getLast().setApiLastSeen(NOW.minus(Duration.ofDays(6)));
        assertThat(first().operationalStatus().value()).isEqualTo("REVISAR");
        assertThat(first().historicalContext().value()).isEqualTo("ATUALMENTE_INATIVO");
        assertThat(report().clan().get("membersOffline").value()).isEqualTo(1);
        snapshots.clear();
        snapshots.add(snapshot(1,1,NOW.minus(Duration.ofDays(3)),0,NOW.minus(Duration.ofDays(10))));
        assertThat(first().operationalStatus().value()).isEqualTo("OBSERVAR");
        assertThat(report().clan().get("membersOffline").value()).isNull();
    }

    @Test void lastSeenAusenteOuFuturoNaoViraOffline() {
        player(1, new int[]{3200,3200,3200,3200}, new int[]{16,16,16,16},198);
        snapshots.getLast().setApiLastSeen(null);
        assertThat(first().metrics().get("offlineDays").value()).isNull();
        assertThat(first().operationalStatus().value()).isEqualTo("OBSERVAR");
        snapshots.getLast().setApiLastSeen(NOW.plusSeconds(60));
        assertThat(first().metrics().get("lastKnownActivity").value()).isNull();
    }

    @Test void baixaAtividadeVaiParaObservacaoAntesDeRevisao() {
        player(1,new int[]{3200,3200,3200,3200},new int[]{16,16,16,16},198);
        snapshots.getLast().setApiLastSeen(NOW.minus(Duration.ofDays(4)));
        assertThat(first().operationalStatus().value()).isEqualTo("OBSERVAR");
        assertThat(first().operationalStatus().reason()).contains("Baixa atividade");
        assertThat(first().elderCandidate().value()).isFalse();
    }

    @Test void reingressoReiniciaTempoNoClaESuspendeCandidatura() {
        player(1, new int[]{3200,3200,3200,3200}, new int[]{16,16,16,16},198);
        events.add(new MembershipEvent(2L,1L,1L,UUID.randomUUID(),"LEAVE",null,null,NOW.minus(Duration.ofDays(2))));
        events.add(new MembershipEvent(3L,1L,1L,UUID.randomUUID(),"JOIN",null,"member",NOW.minus(Duration.ofDays(1))));
        assertThat(first().metrics().get("observedTenureDays").value()).isEqualTo(0L);
        assertThat(first().historicalContext().value()).isEqualTo("NOVO");
        assertThat(first().elderCandidate().value()).isFalse();
    }

    @Test void candidatosExigemTodosOsCriteriosECargoCorreto() {
        player(1, new int[]{3200,3200,3200,3200}, new int[]{16,16,16,16},198);
        assertThat(first().elderCandidate().value()).isTrue();
        snapshots.set(1,snapshot(11,1,NOW.minusSeconds(1800),50,NOW.minusSeconds(2000)));
        assertThat(first().elderCandidate().value()).isFalse();
    }

    @Test void titulosExclusivosPulamPremiadosEUsamDesempateDeterministico() {
        for (int id=1;id<=3;id++) player(id,new int[]{3200,3200,3200,3200},new int[]{16,16,16,16},200);
        var awards=report().highlights();
        assertThat(awards).extracting(Highlight::playerTag).containsExactly("#P01","#P02","#P03");
        assertThat(awards).extracting(Highlight::playerTag).doesNotHaveDuplicates();
        Collections.reverse(players); Collections.reverse(participants);
        assertThat(report().highlights()).extracting(Highlight::playerTag).containsExactly("#P01","#P02","#P03");
    }

    @Test void almaExigeAmostraMinimaEDecksCompletos() {
        player(1,new int[]{3200},new int[]{16},200);
        assertThat(report().highlights().get(2).playerTag()).isNull();
        assertThat(report().highlights().get(2).result().quality()).isEqualTo(Quality.INSUFFICIENT_DATA);
    }

    @Test void almaParcialSomenteQuandoHabilitadaExplicitamente() {
        for (int id=1;id<=3;id++) player(id,new int[]{3000,3000,3000,3000},new int[]{15,15,15,15},200);
        assertThat(report().highlights().get(2).playerTag()).isNull();
        var partial=new AnalyticsEngine(RULES,true).analyze(new AnalyticsEngine.Data(players,wars,participants,snapshots,events),NOW,WEEK);
        assertThat(partial.highlights().get(2).playerTag()).isEqualTo("#P03");
        assertThat(partial.highlights().get(2).result().reason()).contains("parciais habilitadas");
    }

    @Test void vazioEHistoricoFuturoSaoDadosInsuficientes() {
        var empty=report();
        assertThat(empty.clan().get("currentMembers").value()).isNull();
        assertThat(empty.clan().get("averageRecentPoints").quality()).isEqualTo(Quality.INSUFFICIENT_DATA);
        player(1,new int[]{3200},new int[]{16},200);
        var old=engine.analyze(new AnalyticsEngine.Data(players,wars,participants,snapshots,events),NOW,LocalDate.of(2026,6,1));
        assertThat(old.players()).isEmpty();
        assertThat(old.evolution()).isEmpty();
    }

    @Test void decksForaDoPadraoNaoSaoTruncadosParaCemPorCento() {
        player(1,new int[]{3200,3200,3200,3200},new int[]{20,16,16,16},200);
        assertThat(first().metrics().get("recentDecks").value()).isEqualTo(68L);
        assertThat(first().metrics().get("standardDeckTargetPercent").value()).isNull();
    }
}
