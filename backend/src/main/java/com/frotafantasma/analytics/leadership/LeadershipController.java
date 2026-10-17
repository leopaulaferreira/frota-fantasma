package com.frotafantasma.analytics.leadership;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.util.UUID;
import com.frotafantasma.analytics.recommendation.RecommendationDtos.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/leadership")
public class LeadershipController {
    private final LeadershipNotes notes;
    public LeadershipController(LeadershipNotes notes) { this.notes=notes; }
    @GetMapping("/notes/active") public LeadershipNotes.Active active() { return notes.active(); }
    @GetMapping("/players/{tag}/notes") public Page<LeadershipNotes.Note> history(
            @PathVariable @Pattern(regexp="#[A-Z0-9]{3,31}") String tag,
            @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return notes.history(tag,page,size);
    }
    @PostMapping("/players/{tag}/notes") public LeadershipNotes.Note create(
            @PathVariable @Pattern(regexp="#[A-Z0-9]{3,31}") String tag,@RequestBody @Valid LeadershipNotes.Create request,Principal principal) {
        return notes.create(tag,request,principal.getName());
    }
    @PostMapping("/notes/{id}/resolve") public LeadershipNotes.Note resolve(@PathVariable UUID id,
            @RequestBody @Valid LeadershipNotes.Resolve request,Principal principal) {
        return notes.resolve(id,request,principal.getName());
    }
}
