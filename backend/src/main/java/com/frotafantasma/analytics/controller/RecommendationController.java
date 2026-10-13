package com.frotafantasma.analytics.controller;

import static com.frotafantasma.analytics.recommendation.RecommendationDtos.*;
import com.frotafantasma.analytics.recommendation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recommendations")
@Tag(name="Recomendações",description="Regras versionadas, avaliações e títulos locais; nunca altera cargos ou membros na Supercell")
public class RecommendationController {
    private final RecommendationRulesService rules;
    private final RecommendationService recommendations;
    public RecommendationController(RecommendationRulesService rules,RecommendationService recommendations) { this.rules=rules; this.recommendations=recommendations; }
    @GetMapping("/rules") public RuleVersion rules() { return rules.current(); }
    @PostMapping("/rules")
    @Operation(summary="Criar e ativar versão imutável de regras",description="Enviar expectedVersion da leitura atual; null somente antes da primeira versão. Uma revisão anterior continua disponível no histórico.")
    public RuleVersion change(@RequestBody @Valid RuleChange request) { return rules.change(request); }
    @GetMapping("/rules/history")
    public Page<RuleVersion> rulesHistory(@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) { return rules.history(page,size); }
    @PostMapping("/evaluations")
    @Operation(summary="Persistir avaliação e evidências",description="idempotencyKey obrigatório: repetir a chave devolve a avaliação original, mesmo se as regras mudarem. Nova avaliação requer nova chave.")
    public Evaluation evaluate(@RequestBody @Valid EvaluateRequest request) { return recommendations.evaluate(request); }
    @GetMapping("/evaluations")
    public Page<EvaluationSummary> history(@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) { return recommendations.history(page,size); }
    @GetMapping("/evaluations/{id}") public Evaluation evaluation(@PathVariable UUID id) { return recommendations.evaluation(id); }
    @GetMapping("/players/{tag}/history")
    public Page<MemberStatus> memberHistory(@PathVariable @Pattern(regexp="#[A-Z0-9]{3,31}") String tag,
            @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) { return recommendations.playerHistory(tag,page,size); }
    @PostMapping("/evaluations/{id}/finalize-awards")
    @Operation(summary="Formalizar os títulos de uma avaliação encerrada",description="Congela os vencedores locais da semana de publicação. É idempotente para a mesma avaliação e rejeita outra avaliação da semana já formalizada. Limitações das métricas ficam preservadas.")
    public AwardBatch finalizeAwards(@PathVariable UUID id) { return recommendations.finalizeAwards(id); }
    @GetMapping("/awards")
    public Page<ReceivedAward> receivedAwards(@RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) { return recommendations.receivedAwards(null,page,size); }
    @GetMapping("/players/{tag}/awards")
    public Page<ReceivedAward> playerAwards(@PathVariable @Pattern(regexp="#[A-Z0-9]{3,31}") String tag,
            @RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) { return recommendations.receivedAwards(tag,page,size); }
    @GetMapping("/awards/{week}") public AwardBatch awards(@PathVariable LocalDate week) { return recommendations.awards(week); }
}
