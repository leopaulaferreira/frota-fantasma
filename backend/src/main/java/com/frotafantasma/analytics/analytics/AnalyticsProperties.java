package com.frotafantasma.analytics.analytics;

import jakarta.validation.constraints.*;
import java.time.Duration;
import java.time.ZoneId;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("analytics")
public record AnalyticsProperties(
        @DefaultValue("1600") @Min(1) int pointsTarget,
        @DefaultValue("5") @Min(1) int offlineDays,
        @DefaultValue("4") @Min(2) @Max(4) int minimumWars,
        @DefaultValue("4") @Min(2) @Max(4) int immortalMinimumWars,
        @DefaultValue("3") @Min(2) @Max(4) int reviewLowWars,
        @DefaultValue("80") @Min(1) @Max(100) int adequateStandardDeckPercent,
        @DefaultValue("28") @Min(1) int promotionTenureDays,
        @DefaultValue("100") @Min(0) int promotionDonations,
        @DefaultValue("PT2H") @NotNull @DurationMin(minutes = 1) Duration freshness,
        @DefaultValue("UTC") @NotNull ZoneId timezone
) {
    public static final String VERSION = "analytics-v2";
    // Official rules: four Battle Days, four War Decks/day. This is NOT individual availability.
    public static final int STANDARD_WAR_DECKS = 16;
}
