package com.frotafantasma.analytics.recommendation;

import static org.assertj.core.api.Assertions.*;
import static com.frotafantasma.analytics.analytics.AnalyticsDtos.*;
import com.frotafantasma.analytics.recommendation.RecommendationDtos.ScoreSettings;
import java.util.*;
import org.junit.jupiter.api.Test;

class FrotaScoreCalculatorTest {
    Metric<Double> metric(Double n,boolean insufficient,boolean incomplete) {
        return new Metric<>(n,Origin.DERIVED,insufficient?Quality.INSUFFICIENT_DATA:incomplete?Quality.INCOMPLETE:Quality.AVAILABLE,
                new Period(null,null,"TEST"),4,insufficient,incomplete,List.of("input="+n),"fixture");
    }
    PlayerStats player(Double war,Double decks,Double donations,boolean insufficient,boolean incomplete) {
        var metrics=new LinkedHashMap<String,Metric<?>>();
        metrics.put("recentAverage",metric(war,insufficient,false)); metrics.put("standardDeckTargetPercent",metric(decks,insufficient,false));
        metrics.put("weeklyDonations",metric(donations,donations==null,incomplete));
        return new PlayerStats("#P01","P","member",true,metrics,List.of(),List.of(),null,null,null,null);
    }
    ScoreSettings enabled() { return new ScoreSettings(true,45,35,20,3200,200); }
    @Test void pesoZeroNaoAdicionaFontePeriodoOuAmostraAoScore() {
        var p = player(3200.0, 100.0, 200.0, false, false);
        var warPeriod = new Period(java.time.Instant.parse("2026-09-01T00:00:00Z"), java.time.Instant.parse("2026-09-22T00:00:00Z"), "WARS");
        var donationPeriod = new Period(java.time.Instant.parse("2026-10-01T00:00:00Z"), java.time.Instant.parse("2026-10-08T00:00:00Z"), "DONATIONS");
        p.metrics().put("recentAverage", new Metric<>(3200.0, Origin.DERIVED, Quality.AVAILABLE, warPeriod, 4, false, false, List.of("war:1;source=DIRECT_API"), "fixture"));
        p.metrics().put("weeklyDonations", new Metric<>(200.0, Origin.ROYALEAPI_IMPORT, Quality.INCOMPLETE, donationPeriod, 0, false, true, List.of("membership:1;source=ROYALEAPI_IMPORT"), "fixture"));
        var result = FrotaScoreCalculator.calculate(p, new ScoreSettings(true,100,0,0,3200,200));
        var score = result.get("frotaScore");
        assertThat(score.value()).isEqualTo(100.0);
        assertThat(score.period().from()).isEqualTo(warPeriod.from());
        assertThat(score.period().to()).isEqualTo(warPeriod.to());
        assertThat(score.sources()).containsExactly(Origin.DIRECT_API);
        assertThat(score.quality()).isEqualTo(Quality.AVAILABLE);
        assertThat(result.get("frotaScoreDonations").sources()).isEmpty();
        assertThat(result.get("frotaScoreDonations").warsConsidered()).isZero();
    }

    @Test void scoreSoDeDoacoesNaoHerdaGuerrasOuPeriodoDesconhecido() {
        var p = player(null, null, 200.0, true, false);
        var period = new Period(java.time.Instant.parse("2026-10-01T00:00:00Z"), java.time.Instant.parse("2026-10-08T00:00:00Z"), "DONATIONS");
        p.metrics().put("weeklyDonations", new Metric<>(200.0, Origin.SNAPSHOT_DERIVED, Quality.INCOMPLETE, period, 0, false, true, List.of("membership:1;source=DIRECT_API"), "fixture"));
        var score = FrotaScoreCalculator.calculate(p, new ScoreSettings(true,0,0,100,3200,200)).get("frotaScore");
        assertThat(score.value()).isEqualTo(100.0);
        assertThat(score.warsConsidered()).isZero();
        assertThat(score.period().from()).isEqualTo(period.from());
        assertThat(score.period().to()).isEqualTo(period.to());
        assertThat(score.quality()).isEqualTo(Quality.INCOMPLETE);
    }

    @Test void explicaParcelasECalculaPontuacaoIntermediaria() {
        var result=FrotaScoreCalculator.calculate(player(1600.0,50.0,100.0,false,false),enabled());
        assertThat(result.get("frotaScore").value()).isEqualTo(50.0);
        assertThat(result.get("frotaScoreWar").value()).isEqualTo(22.5);
        assertThat(result.get("frotaScoreDecks").value()).isEqualTo(17.5);
        assertThat(result.get("frotaScoreDonations").value()).isEqualTo(10.0);
        assertThat(result.get("frotaScoreWar").reason()).contains("valor/meta");
    }
    @Test void limitaResultadoDeZeroACemSemEstouro() {
        assertThat(FrotaScoreCalculator.calculate(player(99999.0,300.0,99999.0,false,false),enabled()).get("frotaScore").value()).isEqualTo(100.0);
        assertThat(FrotaScoreCalculator.calculate(player(0.0,0.0,0.0,false,false),enabled()).get("frotaScore").value()).isEqualTo(0.0);
    }
    @Test void naoInventaZeroNemRedistribuiPesoQuandoFaltaDado() {
        var result=FrotaScoreCalculator.calculate(player(3200.0,100.0,null,false,true),enabled());
        assertThat(result.get("frotaScore").value()).isNull();
        assertThat(result.get("frotaScore").quality()).isEqualTo(Quality.INSUFFICIENT_DATA);
        assertThat(result.get("frotaScoreWar").value()).isEqualTo(45.0);
        assertThat(result.get("frotaScoreDonations").value()).isNull();
        assertThat(FrotaScoreCalculator.calculate(player(3200.0,100.0,200.0,true,false),enabled()).get("frotaScore").value()).isNull();
    }
    @Test void preservaPeriodoIncompletoMesmoQuandoHaNumero() {
        var result=FrotaScoreCalculator.calculate(player(3200.0,100.0,100.0,false,true),enabled());
        assertThat(result.get("frotaScore").value()).isEqualTo(90.0);
        assertThat(result.get("frotaScore").incompletePeriod()).isTrue();
        assertThat(result.get("frotaScore").quality()).isEqualTo(Quality.INCOMPLETE);
    }
    @Test void podeDesabilitarScoreOuParcelaPorConfiguracaoExplicita() {
        assertThat(FrotaScoreCalculator.calculate(player(null,null,null,true,true),ScoreSettings.defaults()).get("frotaScore").quality()).isEqualTo(Quality.UNAVAILABLE);
        var onlyWar=new ScoreSettings(true,100,0,0,3200,200);
        assertThat(FrotaScoreCalculator.calculate(player(3200.0,null,null,false,true),onlyWar).get("frotaScore").value()).isEqualTo(100.0);
    }
}
