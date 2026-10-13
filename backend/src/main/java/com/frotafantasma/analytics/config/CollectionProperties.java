package com.frotafantasma.analytics.config;

import jakarta.validation.constraints.*;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Set;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("collection")
public record CollectionProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("PT30M") @NotNull @DurationMin(minutes = 1) Duration membersInterval,
        @DefaultValue("PT15M") @NotNull @DurationMin(minutes = 1) Duration warInterval,
        @DefaultValue("PT6H") @NotNull @DurationMin(minutes = 1) Duration logInterval,
        @DefaultValue("PT10S") @NotNull @DurationMin(seconds = 0) Duration initialDelay,
        @DefaultValue("UTC") @NotNull ZoneId timezone,
        @DefaultValue({"THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"}) @NotEmpty Set<DayOfWeek> warDays,
        @DefaultValue("20") @Min(1) @Max(100) int maxPages
) {}
