package com.frotafantasma.analytics.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.hibernate.validator.constraints.time.DurationMin;
import org.hibernate.validator.constraints.time.DurationMax;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "clash-royale")
public record ClashRoyaleProperties(
        @NotBlank String baseUrl,
        @NotBlank @Pattern(regexp = "#[A-Z0-9]{3,31}") String clanTag,
        @NotBlank String apiToken,
        @DefaultValue("5s") @NotNull @DurationMin(millis = 1) @DurationMax(seconds = 30) Duration connectTimeout,
        @DefaultValue("10s") @NotNull @DurationMin(millis = 1) @DurationMax(seconds = 30) Duration readTimeout,
        @DefaultValue("2") @Min(1) @Max(3) int maxAttempts,
        @DefaultValue("500ms") @NotNull @DurationMin(millis = 1) @DurationMax(seconds = 5) Duration retryDelay,
        @DefaultValue("30s") @NotNull @DurationMin(seconds = 1) @DurationMax(minutes = 10) Duration rateLimitCooldown
) {
    @Override
    public String toString() {
        return "ClashRoyaleProperties[clanTag=" + clanTag + ", apiToken=REDACTED]";
    }
}
