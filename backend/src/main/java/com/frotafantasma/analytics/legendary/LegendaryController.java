package com.frotafantasma.analytics.legendary;

import java.time.LocalDate;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/api/legendary-awards")
public class LegendaryController {
    private final LegendaryService service;
    public LegendaryController(LegendaryService service) { this.service=service; }
    @GetMapping
    @Operation(summary="Fantasma Lendário: resultados e histórico do Caminho de Troféus",description="Sem consulta externa. Cada página lista 52 semanas. Resultados experimentais não integram as demais premiações.")
    public LegendaryDtos.Overview read(@RequestParam(required=false) LocalDate week,
                                      @RequestParam(defaultValue="0") @Min(0) @Max(10000) int page) { return service.read(week,page); }
    @PostMapping("/collect")
    @Operation(summary="Retomar coleta idempotente da Ladder",description="Administrador com CSRF. Respeita a janela de fechamento; não recria histórico nem busca troféus retroativos.")
    public LegendaryDtos.Overview collect() { service.collect(); return service.read(null,0); }
}
