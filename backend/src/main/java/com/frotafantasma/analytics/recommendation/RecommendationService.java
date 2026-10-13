package com.frotafantasma.analytics.recommendation;

import static com.frotafantasma.analytics.recommendation.RecommendationDtos.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.frotafantasma.analytics.analytics.AnalyticsService;
import com.frotafantasma.analytics.analytics.DonationCalculator;
import com.frotafantasma.analytics.config.ClashRoyaleProperties;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RecommendationService {
    private final RecommendationRepository repository;
    private final RecommendationRulesService rules;
    private final AnalyticsService analytics;
    private final ClashRoyaleProperties api;
    private final Clock clock;
    private final ObjectMapper json;
    public RecommendationService(RecommendationRepository repository,RecommendationRulesService rules,AnalyticsService analytics,
                                 ClashRoyaleProperties api,Clock clock,ObjectMapper json) {
        this.repository=repository; this.rules=rules; this.analytics=analytics; this.api=api; this.clock=clock; this.json=json;
    }
    @Transactional
    public Evaluation evaluate(EvaluateRequest request) {
        if (request.idempotencyKey()==null || request.idempotencyKey().isBlank() || request.idempotencyKey().length()>100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"idempotencyKey é obrigatório e deve ter até 100 caracteres.");
        repository.lock(api.clanTag());
        var settings=rules.current();
        LocalDate week=request.week()==null?DonationCalculator.week(clock.instant(),settings.settings().analytics().timezone()):request.week();
        var existing=repository.byKey(api.clanTag(),request.idempotencyKey());
        if (existing.isPresent()) {
            if (!existing.get().week().equals(week)) throw conflict("Chave de idempotência já usada para outra semana.");
            return existing.get();
        }
        var version=rules.ensureStored();
        var report=analytics.reportWithRules(week,version);
        Instant end=week.plusWeeks(1).atStartOfDay(version.settings().analytics().timezone()).toInstant();
        Instant cutoff=report.generatedAt().isBefore(end)?report.generatedAt():end;
        var evaluation=new Evaluation(UUID.randomUUID(),week,clock.instant(),cutoff,version.id(),report.ruleVersion(),json.valueToTree(report));
        repository.insertEvaluation(api.clanTag(),request.idempotencyKey(),evaluation);
        for (var p:report.players()) {
            if (p.currentMember() && p.operationalStatus().value()!=null)
                repository.insertStatus(evaluation.id(),p.tag(),p.operationalStatus().value(),RecommendationPolicy.color(p.operationalStatus().value()),p);
        }
        return repository.evaluation(api.clanTag(),evaluation.id()).orElseThrow();
    }
    public Evaluation evaluation(UUID id) {
        return repository.evaluation(api.clanTag(),id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Avaliação não encontrada neste clã."));
    }
    public Page<EvaluationSummary> history(int page,int size) {
        return new Page<>(repository.evaluations(api.clanTag(),page,size),page,size,repository.countEvaluations(api.clanTag()));
    }
    public Page<MemberStatus> playerHistory(String tag,int page,int size) {
        return new Page<>(repository.statuses(api.clanTag(),tag,page,size),page,size,repository.countStatuses(api.clanTag(),tag));
    }
    @Transactional
    public AwardBatch finalizeAwards(UUID evaluationId) {
        repository.lock(api.clanTag());
        var evaluation=evaluation(evaluationId);
        var existing=repository.awards(api.clanTag(),evaluation.week());
        if (existing.isPresent()) {
            if (!existing.get().evaluationId().equals(evaluationId)) throw conflict("Esta semana já tem premiações definitivas vinculadas a outra avaliação.");
            return existing.get();
        }
        String timezone=evaluation.report().path("rules").path("timezone").asText();
        Instant end=evaluation.week().plusWeeks(1).atStartOfDay(ZoneId.of(timezone)).toInstant();
        if (clock.instant().isBefore(end) || evaluation.analysisCutoff().isBefore(end))
            throw conflict("Finalize somente uma avaliação gerada após o encerramento da semana de publicação.");
        var awards=new ArrayList<FinalAward>();
        Set<String> players=new HashSet<>();
        for (var h:evaluation.report().path("highlights")) {
            if (h.path("playerTag").isNull() || h.path("playerTag").isMissingNode()) continue;
            if (h.path("result").path("value").isNull() || h.path("result").path("insufficientSample").asBoolean())
                throw conflict("Há um título com amostra insuficiente; gere uma avaliação com evidências suficientes.");
            String player=h.path("playerTag").asText();
            if (!players.add(player)) throw conflict("Um jogador não pode receber dois títulos na mesma semana.");
            awards.add(new FinalAward(h.path("title").asText(),player,h.deepCopy()));
        }
        if (awards.isEmpty()) throw conflict("Nenhum título elegível para formalizar. Dados insuficientes.");
        var batch=new AwardBatch(evaluation.week(),evaluation.id(),clock.instant(),timezone,List.copyOf(awards));
        repository.insertAwards(api.clanTag(),batch);
        return repository.awards(api.clanTag(),evaluation.week()).orElseThrow();
    }
    public Page<ReceivedAward> receivedAwards(String tag, int page, int size) {
        return repository.receivedAwards(api.clanTag(), tag, page, size);
    }
    public AwardBatch awards(LocalDate week) {
        return repository.awards(api.clanTag(),week).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Ainda não há premiação definitiva nesta semana."));
    }
    private ResponseStatusException conflict(String reason) { return new ResponseStatusException(HttpStatus.CONFLICT,reason); }
}
