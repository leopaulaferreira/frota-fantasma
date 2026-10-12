package com.frotafantasma.analytics.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "clash-royale")
public record ClashRoyaleProperties(
        @NotBlank String baseUrl,
        @NotBlank @Pattern(regexp = "#[A-Z0-9]{3,}") String clanTag,
        @NotBlank String apiToken
) {
}

