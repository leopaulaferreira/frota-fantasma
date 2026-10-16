package com.frotafantasma.analytics.recommendation;

import java.time.Clock;
import java.time.LocalDate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recommendations/automation")
public class AutomaticAwardsController {
    private final AutomaticAwardsService service;
    private final Clock clock;
    private final boolean enabled;
    public AutomaticAwardsController(AutomaticAwardsService service, Clock clock,
            @Value("${awards.automatic.enabled:true}") boolean enabled,
            @Value("${collection.enabled:true}") boolean collectionEnabled) {
        this.service=service; this.clock=clock; this.enabled=enabled && collectionEnabled;
    }
    public record Status(boolean enabled, String timezone, String schedule, LocalDate closingWeek, boolean pending) {}
    @GetMapping public Status status() {
        return new Status(enabled, AutomaticAwardsService.ZONE.toString(), "Segunda-feira às 06h",
                AutomaticAwardsService.closingWeek(clock.instant()), service.pending());
    }
}
