package com.frotafantasma.analytics.controller;

import com.frotafantasma.analytics.dto.HistoryDtos.*;
import com.frotafantasma.analytics.dto.CollectionResult;
import com.frotafantasma.analytics.mapper.HistoryMapper;
import com.frotafantasma.analytics.service.HistoryQueryService;
import com.frotafantasma.analytics.service.CollectionCoordinator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@Tag(name = "Histórico", description = "Dados persistidos; datas de observação não equivalem a último login")
public class HistoryController {
    private final HistoryQueryService history;
    private final CollectionCoordinator collections;
    public HistoryController(HistoryQueryService history, CollectionCoordinator collections) {
        this.history = history; this.collections = collections;
    }
    @GetMapping("/clan/history")
    public PageResponse<ClanSnapshotResponse> clanHistory(@RequestParam(defaultValue="0") @Min(0) int page, @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return history.clanHistory(page, size);
    }
    @GetMapping("/clan/events")
    public PageResponse<EventResponse> events(@RequestParam(defaultValue="0") @Min(0) int page, @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return history.events(null, page, size);
    }
    @GetMapping("/players/{tag}")
    public PlayerResponse player(@PathVariable @Pattern(regexp="#[A-Z0-9]{3,31}") String tag) {
        return HistoryMapper.player(history.player(tag));
    }
    @GetMapping("/players/{tag}/history")
    public PageResponse<MembershipResponse> membership(@PathVariable @Pattern(regexp="#[A-Z0-9]{3,31}") String tag,
            @RequestParam(defaultValue="0") @Min(0) int page, @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return history.memberships(tag, page, size);
    }
    @GetMapping("/players/{tag}/events")
    public PageResponse<EventResponse> events(@PathVariable @Pattern(regexp="#[A-Z0-9]{3,31}") String tag,
            @RequestParam(defaultValue="0") @Min(0) int page, @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return history.events(tag, page, size);
    }
    @GetMapping("/players/{tag}/wars")
    public PageResponse<PlayerWarResponse> playerWars(@PathVariable @Pattern(regexp="#[A-Z0-9]{3,31}") String tag,
            @RequestParam(defaultValue="0") @Min(0) int page, @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return history.playerWars(tag, page, size);
    }
    @GetMapping("/wars")
    public PageResponse<WarSummary> wars(@RequestParam(defaultValue="0") @Min(0) int page, @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return history.wars(page, size);
    }
    @GetMapping("/wars/{id}")
    public WarDetail war(@PathVariable @Positive Long id) { return history.war(id); }
    @GetMapping("/wars/current")
    public RiverResponse current() { return history.currentRiver(); }
    @GetMapping("/wars/current/history")
    public PageResponse<RiverSummary> riverHistory(@RequestParam(defaultValue="0") @Min(0) int page, @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return history.riverHistory(page, size);
    }
    @GetMapping("/wars/current/history/{id}")
    public RiverResponse river(@PathVariable @Positive Long id) { return history.river(id); }
    @GetMapping("/collections")
    public PageResponse<CollectionResult> runs(@RequestParam(defaultValue="0") @Min(0) int page, @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return history.runs(page, size);
    }
    @PostMapping("/collections/sync")
    @Operation(summary="Coletar clã, membros, corrida atual e histórico oficial", description="Cada resultado informa SUCCESS, FAILED ou SKIPPED. O HTTP 200 não significa que todas as fontes responderam.")
    public List<CollectionResult> sync() { return collections.sync(); }
}
