package com.frotafantasma.analytics.recommendation;

import static com.frotafantasma.analytics.analytics.AnalyticsDtos.*;
import static com.frotafantasma.analytics.recommendation.RecommendationDtos.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public final class RecommendationPolicy {
    private RecommendationPolicy() {}
    public static Report apply(Report report,RuleVersion rules,List<Alert> alerts,Instant cutoff) {
        var byPlayer=alerts.stream().collect(Collectors.groupingBy(Alert::playerTag));
        var players=new ArrayList<PlayerStats>();
        for (var p:report.players()) {
            var previous=byPlayer.getOrDefault(p.tag(),List.of());
            var evidence=previous.stream().map(a->"evaluation:"+a.evaluationId()+":"+a.status()+"@"+a.analysisCutoff()).toList();
            var metrics=new LinkedHashMap<>(p.metrics());
            metrics.putAll(FrotaScoreCalculator.calculate(p,rules.settings().score()));
            metrics.put("recentAlerts",new Metric<>(previous.size(),Origin.SNAPSHOT_DERIVED,Quality.INCOMPLETE,
                    new Period(cutoff.minus(Duration.ofDays(rules.settings().recentAlertDays())),cutoff,"PERSISTED_EVALUATION_CUTOFFS"),0,false,true,evidence,
                    "Avaliações OBSERVAR/REVISAR persistidas na janela; contagem de avaliações, não de guerras distintas. Zero significa nenhum alerta registrado, não monitoramento contínuo."));
            metrics.put("operationalColor",new Metric<>(color(p.operationalStatus().value()),Origin.DERIVED,p.operationalStatus().quality(),p.operationalStatus().period(),p.operationalStatus().warsConsidered(),
                    p.operationalStatus().insufficientSample(),p.operationalStatus().incompletePeriod(),p.operationalStatus().dataUsed(),"MANTER=GREEN, OBSERVAR=YELLOW, REVISAR=RED; classificação analítica."));
            var candidate=p.elderCandidate();
            if (p.currentMember() && !previous.isEmpty()) {
                var used=new ArrayList<>(candidate.dataUsed()); used.addAll(evidence);
                candidate=new Metric<>(false,Origin.SNAPSHOT_DERIVED,candidate.quality(),candidate.period(),candidate.warsConsidered(),candidate.insufficientSample(),candidate.incompletePeriod(),used,
                        candidate.reason()+" Candidatura bloqueada por "+previous.size()+" alerta(s) persistido(s) nos últimos "+rules.settings().recentAlertDays()+" dias. Registros e regras originais são preservados.");
            }
            players.add(new PlayerStats(p.tag(),p.name(),p.role(),p.currentMember(),metrics,p.wars(),p.donations(),p.participation(),p.historicalContext(),p.operationalStatus(),candidate));
        }
        return new Report(report.generatedAt(),report.week(),com.frotafantasma.analytics.analytics.AnalyticsProperties.VERSION,report.rules(),List.copyOf(players),report.clan(),report.highlights(),report.evolution(),rules);
    }
    public static String color(String status) {
        if (status==null) return null;
        return switch(status) { case "MANTER" -> "GREEN"; case "OBSERVAR" -> "YELLOW"; case "REVISAR" -> "RED"; default -> throw new IllegalArgumentException("Unknown analytic status"); };
    }
}
