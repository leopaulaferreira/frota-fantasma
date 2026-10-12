package com.frotafantasma.analytics.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ClashRoyalePropertiesTest {
    @Test
    void rejeitaTagComPathTimeoutInfinitoERetrySemLimite() {
        var properties = new ClashRoyaleProperties("https://api.clashroyale.com/v1", "#RQJGC9GV/members",
                "test-only-secret", Duration.ZERO, Duration.ofSeconds(10), 99,
                Duration.ofMillis(500), Duration.ofSeconds(30));
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(properties))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .containsExactlyInAnyOrder("clanTag", "connectTimeout", "maxAttempts");
        }
        assertThat(properties.toString()).doesNotContain("test-only-secret");
    }
}
