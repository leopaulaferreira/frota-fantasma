package com.frotafantasma.analytics.legendary;

import java.time.Duration;
import java.time.Instant;
import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("awards.legendary")
public record LegendaryProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("false") boolean official,
        @DefaultValue("OBSERVED") Source recordSource,
        @DefaultValue("trophy-road-v1") @Pattern(regexp="[a-zA-Z0-9._-]{1,100}") String recordEra,
        @DefaultValue("2026-11-25T00:00:00Z") Instant eraStart,
        @DefaultValue("14000") @Min(1) int trophyLimit,
        @DefaultValue("PT2H") Duration captureWindow) {
    public enum Source { OBSERVED, OFFICIAL_API }
    @AssertTrue(message="A janela de coleta deve ser positiva e não exceder 6 horas")
    public boolean isValidWindow() {
        return captureWindow != null && captureWindow.isPositive() && captureWindow.compareTo(Duration.ofHours(6)) <= 0;
    }
}
