package com.frotafantasma.analytics.analytics;

import static org.assertj.core.api.Assertions.*;
import jakarta.validation.Validation;
import java.time.*;
import org.junit.jupiter.api.Test;

class AnalyticsPropertiesTest {
    @Test void validaFaixasDasRegrasAnaliticas() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(AnalyticsEngineTest.RULES)).isEmpty();
            var invalid = new AnalyticsProperties(0, 0, 1, 5, 1, 101, 0, -1, Duration.ZERO, null);
            assertThat(validator.validate(invalid)).hasSize(10);
        }
    }
}
