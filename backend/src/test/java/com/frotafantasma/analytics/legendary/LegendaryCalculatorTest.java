package com.frotafantasma.analytics.legendary;

import static com.frotafantasma.analytics.legendary.LegendaryDtos.*;
import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class LegendaryCalculatorTest {
    private Snapshot snapshot(String tag,int trophies,int record) {
        return new Snapshot(tag,"Nome","member",true,trophies,record,record,Instant.EPOCH,"#CLAN",null,1);
    }
    private Candidate calculate(int previousTrophies,int previousRecord,int trophies,int record) {
        return LegendaryCalculator.candidate(snapshot("#AAA",trophies,record),snapshot("#AAA",previousTrophies,previousRecord),previousRecord,new WarScore(1600,16),"#CLAN");
    }
    @Test void recuperacaoNaoContaComoSuperacao() {
        var c=calculate(10500,11000,11200,11200);
        assertThat(c.netChange()).isEqualTo(700); assertThat(c.improvement()).isEqualTo(200); assertThat(c.eligible()).isTrue();
    }
    @Test void recuperarRecordeSemUltrapassarNaoPremia() {
        var c=calculate(10500,11000,11000,11000);
        assertThat(c.netChange()).isEqualTo(500); assertThat(c.improvement()).isZero(); assertThat(c.eligible()).isFalse();
    }
    @Test void exemplosDoPedido() {
        assertThat(calculate(10500,10500,10800,10800).improvement()).isEqualTo(300);
        assertThat(calculate(12000,12000,11900,12000).improvement()).isZero();
        assertThat(calculate(9500,9500,10100,10100).improvement()).isEqualTo(600);
        assertThat(calculate(14000,14000,14000,14000).improvement()).isZero();
    }
    @Test void aumentoDeUmTrofeuBasta() { assertThat(calculate(9000,9000,9001,9001).eligible()).isTrue(); }
    @Test void primeiraColetaNaoTemVencedor() {
        var c=LegendaryCalculator.candidate(snapshot("#AAA",10000,10000),null,null,new WarScore(3200,16),"#CLAN");
        assertThat(c.previousRecord()).isNull(); assertThat(c.improvement()).isNull(); assertThat(c.eligible()).isFalse();
    }
    @Test void dadosAusentesNuncaSaoZero() {
        var missing=new Snapshot("#AAA","Nome","member",true,null,null,null,null,null,"UPSTREAM_TIMEOUT",2);
        var c=LegendaryCalculator.candidate(missing,snapshot("#AAA",9000,9000),9000,null,"#CLAN");
        assertThat(c.trophies()).isNull(); assertThat(c.netChange()).isNull(); assertThat(c.warMedals()).isNull();
        assertThat(c.improvement()).isNull(); assertThat(c.eligible()).isFalse();
    }
    @Test void todasAsObrigacoesSaoNecessarias() {
        var now=new Snapshot("#AAA","Nome","leader",false,10000,10000,10000,Instant.EPOCH,"#OTHER",null,1);
        var c=LegendaryCalculator.candidate(now,snapshot("#AAA",9000,9000),9000,new WarScore(1599,15),"#CLAN");
        assertThat(c.reasons()).hasSize(4); assertThat(c.eligible()).isFalse();
    }
    @Test void decksAcimaDe16SaoInconsistentes() {
        var c=LegendaryCalculator.candidate(snapshot("#AAA",10000,10000),snapshot("#AAA",9000,9000),9000,new WarScore(2000,17),"#CLAN");
        assertThat(c.eligible()).isFalse();
    }
    @Test void quedaDoRecordeExigeRevisao() {
        var c=calculate(9000,10000,9500,9500);
        assertThat(c.eligible()).isFalse(); assertThat(c.reasons()).anyMatch(r->r.contains("incompatível"));
    }
    @Test void highWaterImpedeRecuperacaoAposFalhaDeUmaSemana() {
        var c=LegendaryCalculator.candidate(snapshot("#AAA",10000,10000),snapshot("#AAA",9000,9000),10000,new WarScore(2000,16),"#CLAN");
        assertThat(c.improvement()).isZero(); assertThat(c.eligible()).isFalse();
    }
    @Test void desempatePorSuperacaoMedalhasETag() {
        Candidate a=LegendaryCalculator.candidate(snapshot("#AAA",10100,10100),snapshot("#AAA",10000,10000),10000,new WarScore(2000,16),"#CLAN");
        Candidate b=LegendaryCalculator.candidate(snapshot("#BBB",10100,10100),snapshot("#BBB",10000,10000),10000,new WarScore(2000,16),"#CLAN");
        Candidate c=LegendaryCalculator.candidate(snapshot("#CCC",10100,10100),snapshot("#CCC",10000,10000),10000,new WarScore(2100,16),"#CLAN");
        Candidate d=LegendaryCalculator.candidate(snapshot("#DDD",10101,10101),snapshot("#DDD",10000,10000),10000,new WarScore(1600,16),"#CLAN");
        assertThat(LegendaryCalculator.rank(List.of(b,a,c,d))).extracting(Candidate::tag).containsExactly("#DDD","#CCC","#AAA","#BBB");
        assertThat(LegendaryCalculator.rank(List.of(b,a,c,d))).extracting(Candidate::position).containsExactly(1,2,3,4);
    }
    @Test void inelegiveisNaoRecebemPosicao() {
        assertThat(LegendaryCalculator.rank(List.of(calculate(9000,9000,9000,9000))).getFirst().position()).isNull();
    }
}
