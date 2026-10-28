package com.frotafantasma.analytics.imports.controller;

import com.frotafantasma.analytics.dto.HistoryDtos.PageResponse;
import com.frotafantasma.analytics.imports.dto.ImportDtos.*;
import com.frotafantasma.analytics.imports.parser.RoyaleApiCsvParser;
import com.frotafantasma.analytics.imports.service.RoyaleApiImportService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/imports/royaleapi")
public class RoyaleApiImportController {
    private static final String PREVIEW = RoyaleApiImportController.class.getName() + ".preview";
    private record Ticket(String token, String checksum, String filename, Instant expiresAt) implements java.io.Serializable {}
    private final RoyaleApiCsvParser parser;
    private final RoyaleApiImportService service;
    private final Clock clock;
    public RoyaleApiImportController(RoyaleApiCsvParser parser, RoyaleApiImportService service, Clock clock) {
        this.parser = parser; this.service = service; this.clock = clock;
    }
    @PostMapping(value="/preview", consumes="multipart/form-data")
    public PreviewResponse preview(@RequestParam("file") MultipartFile file, HttpSession session) {
        session.removeAttribute(PREVIEW);
        var parsed = parser.parse(file);
        var analysis = service.preview(parsed);
        String token = UUID.randomUUID().toString();
        session.setAttribute(PREVIEW, new Ticket(token, parsed.checksum(), parsed.filename(), clock.instant().plusSeconds(900)));
        return new PreviewResponse(analysis, token);
    }
    @PostMapping(consumes="multipart/form-data")
    public Result confirm(@RequestParam("file") MultipartFile file, @RequestParam String previewToken, HttpSession session, java.security.Principal actor) {
        var parsed = parser.parse(file);
        var ticket = (Ticket) session.getAttribute(PREVIEW);
        if (ticket == null || !ticket.token().equals(previewToken) || !ticket.checksum().equals(parsed.checksum())
                || !ticket.filename().equals(parsed.filename()) || !clock.instant().isBefore(ticket.expiresAt()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Analise novamente este arquivo antes de confirmar; a prévia mudou ou expirou.");
        return service.confirm(parsed, actor.getName());
    }
    @GetMapping
    public PageResponse<Batch> history(@RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="10") @Min(1) @Max(50) int size) { return service.history(page, size); }
}
