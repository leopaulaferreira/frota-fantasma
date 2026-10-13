package com.frotafantasma.analytics.recommendation;

import static com.frotafantasma.analytics.analytics.AnalyticsDtos.*;
import com.frotafantasma.analytics.recommendation.RecommendationDtos.ScoreSettings;
import java.util.*;

public final class FrotaScoreCalculator {
    private FrotaScoreCalculator() {}
    public static Map<String,Metric<?>> calculate(PlayerStats player, ScoreSettings settings) {
        var result=new LinkedHashMap<String,Metric<?>>();
        var average=player.metrics().get("recentAverage");
        if (!settings.enabled()) {
            result.put("frotaScore",new Metric<>(null,Origin.SNAPSHOT_DERIVED,Quality.UNAVAILABLE,average.period(),average.warsConsidered(),false,average.incompletePeriod(),List.of(),"Frota Score desabilitado nesta versão das regras."));
            return result;
        }
        var war=component(player.metrics().get("recentAverage"),settings.warTarget(),settings.warWeight(),"guerra");
        var decks=component(player.metrics().get("standardDeckTargetPercent"),100,settings.decksWeight(),"uso do padrão de decks (não disponibilidade individual)");
        var donations=component(player.metrics().get("weeklyDonations"),settings.donationTarget(),settings.donationsWeight(),"doações observadas; limite inferior");
        result.put("frotaScoreWar",war); result.put("frotaScoreDecks",decks); result.put("frotaScoreDonations",donations);
        var parts=List.of(war,decks,donations);
        boolean missing=parts.stream().anyMatch(p->p.value()==null);
        boolean incomplete=parts.stream().anyMatch(Metric::incompletePeriod);
        var used=parts.stream().flatMap(p->p.dataUsed().stream()).toList();
        Double score=missing?null:parts.stream().mapToDouble(Metric::value).sum();
        result.put("frotaScore",new Metric<>(score,Origin.SNAPSHOT_DERIVED,missing?Quality.INSUFFICIENT_DATA:incomplete?Quality.INCOMPLETE:Quality.AVAILABLE,
                new Period(average.period().from(),player.metrics().get("weeklyDonations").period().to(),"WAR_PUBLICATIONS_AND_DONATION_WEEK; see component periods"),
                average.warsConsidered(),missing,incomplete,used,"Soma das parcelas ponderadas, entre 0 e 100. Pesos="+settings.warWeight()+"/"+settings.decksWeight()+"/"+settings.donationsWeight()
                        +". Não redistribui pesos nem substitui dados ausentes por zero. Doações incompletas tornam o resultado um limite inferior; score não decide promoção ou expulsão."));
        return result;
    }
    private static Metric<Double> component(Metric<?> input,double target,int weight,String name) {
        boolean missing=weight>0 && (!(input.value() instanceof Number) || input.insufficientSample() || input.quality()==Quality.UNAVAILABLE);
        Double value=missing?null:weight==0?0.0:weight*Math.max(0,Math.min(1,((Number)input.value()).doubleValue()/target));
        var evidence=new ArrayList<>(input.dataUsed()); evidence.add("input="+input.value()+";target="+target+";weight="+weight);
        return new Metric<>(value,input.origin(),missing?Quality.INSUFFICIENT_DATA:weight>0&&input.incompletePeriod()?Quality.INCOMPLETE:Quality.AVAILABLE,
                input.period(),input.warsConsidered(),missing,weight>0&&input.incompletePeriod(),evidence,
                "Parcela "+name+": peso × min(1,max(0,valor/meta)). Peso zero exclui a parcela; a contribuição zero é da regra, não um dado inventado.");
    }
}
