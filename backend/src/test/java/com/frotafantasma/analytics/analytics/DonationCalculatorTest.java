package com.frotafantasma.analytics.analytics;

import static org.assertj.core.api.Assertions.*;
import com.frotafantasma.analytics.entity.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class DonationCalculatorTest {
    ClanMembershipSnapshot row(long id,String at,Integer donations) {
        return AnalyticsEngineTest.snapshot(id,1,Instant.parse(at),donations,null);
    }
    @Test void resetNuncaSubtraiDoacoesENaoSomaContadoresRepetidos() {
        var rows=List.of(row(1,"2026-10-05T10:00:00Z",100),row(2,"2026-10-05T11:00:00Z",180),
                row(3,"2026-10-05T12:00:00Z",5),row(4,"2026-10-05T13:00:00Z",25),row(5,"2026-10-05T14:00:00Z",25));
        var p=DonationCalculator.calculate(rows,List.of(),ZoneOffset.UTC,Instant.parse("2026-10-12T00:00:00Z")).getFirst();
        assertThat(p.observedDonations().value()).isEqualTo(105L);
        assertThat(p.detectedResets()).isEqualTo(1);
        assertThat(p.observedDonations().incompletePeriod()).isTrue();
    }
    @Test void trocaDeSemanaSemQuedaVisivelNaoAutorizaDiferencaEntrePeriodos() {
        var rows=List.of(row(1,"2026-10-11T23:00:00Z",50),row(2,"2026-10-12T10:00:00Z",100),row(3,"2026-10-12T11:00:00Z",130));
        var periods=DonationCalculator.calculate(rows,List.of(),ZoneOffset.UTC,Instant.parse("2026-10-12T12:00:00Z"));
        assertThat(periods.getFirst().observedDonations().value()).isNull();
        assertThat(periods.getLast().observedDonations().value()).isEqualTo(30L);
    }
    @Test void reingressoELacunaComValorAusenteNaoConectamContadores() {
        var rows=List.of(row(1,"2026-10-05T10:00:00Z",100),row(2,"2026-10-05T12:00:00Z",5),row(3,"2026-10-05T13:00:00Z",null),row(4,"2026-10-05T14:00:00Z",30));
        var events=List.of(new MembershipEvent(1L,1L,1L,UUID.randomUUID(),"JOIN",null,null,Instant.parse("2026-10-05T11:00:00Z")));
        assertThat(DonationCalculator.calculate(rows,events,ZoneOffset.UTC,Instant.parse("2026-10-12T00:00:00Z")).getFirst().observedDonations().value()).isNull();
    }
    @Test void detectaResetEntreSemanasSemCreditarContadorInicial() {
        var rows=List.of(row(1,"2026-10-11T23:00:00Z",500),row(2,"2026-10-12T10:00:00Z",10),row(3,"2026-10-12T11:00:00Z",30));
        var p=DonationCalculator.calculate(rows,List.of(),ZoneOffset.UTC,Instant.parse("2026-10-12T12:00:00Z")).getLast();
        assertThat(p.detectedResets()).isEqualTo(1);
        assertThat(p.observedDonations().value()).isEqualTo(20L);
    }
    @Test void respeitaFusoECorteHistorico() {
        var rows=List.of(row(1,"2026-10-12T01:00:00Z",5),row(2,"2026-10-12T02:00:00Z",15),row(3,"2026-10-12T04:00:00Z",100));
        var periods=DonationCalculator.calculate(rows,List.of(),ZoneId.of("America/Sao_Paulo"),Instant.parse("2026-10-12T03:00:00Z"));
        assertThat(periods).hasSize(1);
        assertThat(periods.getFirst().week()).isEqualTo(LocalDate.of(2026,10,5));
        assertThat(periods.getFirst().observedDonations().value()).isEqualTo(10L);
    }
}
