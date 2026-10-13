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
