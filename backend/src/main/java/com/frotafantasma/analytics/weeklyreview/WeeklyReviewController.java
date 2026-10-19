package com.frotafantasma.analytics.weeklyreview;

import java.time.LocalDate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/leadership/weekly-review")
public class WeeklyReviewController {
    private final WeeklyReviewService service;
    public WeeklyReviewController(WeeklyReviewService service) { this.service = service; }
    @GetMapping public WeeklyReviewEngine.Report report(@RequestParam(required=false) LocalDate week) { return service.report(week); }
}
