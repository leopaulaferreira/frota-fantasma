package com.frotafantasma.analytics.weeklyreview;

import java.time.LocalDate;
import java.security.Principal;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/leadership/weekly-review")
public class WeeklyReviewController {
    private final WeeklyReviewService service;
    private final WeeklyReviewDecisions decisions;
    public WeeklyReviewController(WeeklyReviewService service, WeeklyReviewDecisions decisions) { this.service = service; this.decisions=decisions; }
    @GetMapping("/decisions") public List<WeeklyReviewDecisions.Decision> decisions(@RequestParam LocalDate week) { return decisions.history(week); }
    @PostMapping("/decisions/{tag}") public WeeklyReviewDecisions.Decision decide(
        @PathVariable @Pattern(regexp="#[A-Z0-9]{3,31}") String tag,
        @RequestBody @Valid WeeklyReviewDecisions.Create request, Principal principal) {
        return decisions.create(tag,request,principal.getName());
    }
    @GetMapping public WeeklyReviewEngine.Report report(@RequestParam(required=false) LocalDate week) { return service.report(week); }
}
