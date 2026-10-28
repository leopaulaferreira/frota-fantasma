package com.frotafantasma.analytics.imports.controller;

import com.frotafantasma.analytics.imports.dto.BackfillDtos.*;
import com.frotafantasma.analytics.imports.service.HistoricalBackfillService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.*;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/imports/royaleapi/{batchId}/backfill")
public class HistoricalBackfillController {
    private record Ticket(UUID batch,long resolution,String version,Instant expires) implements java.io.Serializable {}
    private final HistoricalBackfillService service;
    private final Clock clock;
    public HistoricalBackfillController(HistoricalBackfillService service,Clock clock) { this.service=service; this.clock=clock; }
    @GetMapping("/preview") public Plan preview(@PathVariable UUID batchId,HttpSession session) {
        var plan=service.preview(batchId);
        session.setAttribute("backfill.preview",new Ticket(batchId,plan.resolutionId(),plan.evidenceVersion(),clock.instant().plusSeconds(900)));
        return plan;
    }
    @PostMapping public Result incorporate(@PathVariable UUID batchId,@Valid @RequestBody Confirm request,HttpSession session,Principal actor) {
        var ticket=(Ticket)session.getAttribute("backfill.preview");
        if(ticket==null || !ticket.batch().equals(batchId) || ticket.resolution()!=request.resolutionId()
                || !ticket.version().equals(request.evidenceVersion()) || !clock.instant().isBefore(ticket.expires()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Reabra a prévia deste lote antes de incorporar.");
        return service.incorporate(batchId,request,actor.getName());
    }
}
