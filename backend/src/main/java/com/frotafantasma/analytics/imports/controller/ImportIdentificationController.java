package com.frotafantasma.analytics.imports.controller;

import com.frotafantasma.analytics.imports.dto.IdentificationDtos.*;
import com.frotafantasma.analytics.imports.service.ImportIdentificationService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/imports/royaleapi/{batchId}/identification")
public class ImportIdentificationController {
    private final ImportIdentificationService service;
    public ImportIdentificationController(ImportIdentificationService service) { this.service=service; }
    @GetMapping public Identification identify(@PathVariable UUID batchId) { return service.identify(batchId); }
    @PostMapping public Resolution resolve(@PathVariable UUID batchId, @Valid @RequestBody Resolve request, Principal actor) {
        return service.resolve(batchId,request,actor.getName());
    }
}
