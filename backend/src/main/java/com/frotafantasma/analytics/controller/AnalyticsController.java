package com.frotafantasma.analytics.controller;

import com.frotafantasma.analytics.analytics.AnalyticsDtos.*;
import com.frotafantasma.analytics.analytics.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/analytics")
@Tag(name = "Analytics", description = "Indicadores explicáveis, classificações e sugestões; nunca executa promoção ou expulsão")
public class AnalyticsController {
    private final AnalyticsService analytics;
    public AnalyticsController(AnalyticsService analytics) { this.analytics = analytics; }

    @GetMapping
    @Operation(summary = "Relatório analítico completo", description = "week: segunda-feira YYYY-MM-DD; padrão semana atual. Cada métrica informa origem, período, amostra, evidências e limitações. Usa somente dados locais.")
    public Report report(@RequestParam(required = false) LocalDate week) { return analytics.report(week); }

    @GetMapping("/players")
    public List<PlayerStats> players(@RequestParam(required = false) LocalDate week) { return analytics.report(week).players(); }

    @GetMapping("/players/{tag}")
    public PlayerStats player(@PathVariable @Pattern(regexp = "#[A-Z0-9]{3,31}") String tag,
                              @RequestParam(required = false) LocalDate week) {
        return analytics.report(week).players().stream().filter(p -> p.tag().equals(tag)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Jogador ainda não observado neste clã."));
    }

    @GetMapping("/clan")
    public Map<String, Metric<?>> clan(@RequestParam(required = false) LocalDate week) { return analytics.report(week).clan(); }

    @GetMapping("/highlights")
    public List<Highlight> highlights(@RequestParam(required = false) LocalDate week) { return analytics.report(week).highlights(); }

    @GetMapping("/promotions")
    @Operation(summary = "Avaliação dos membros para Ancião", description = "Inclui aprovados e não elegíveis com evidências e critérios; não altera cargos.")
    public List<PlayerStats> promotions(@RequestParam(required = false) LocalDate week) {
        return analytics.report(week).players().stream().filter(PlayerStats::currentMember).filter(p -> "member".equals(p.role())).toList();
    }

    @GetMapping("/evolution")
    public List<WeekEvolution> evolution(@RequestParam(required = false) LocalDate week) { return analytics.report(week).evolution(); }
}
