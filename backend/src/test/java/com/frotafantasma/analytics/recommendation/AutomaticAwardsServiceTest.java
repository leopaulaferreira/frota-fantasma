package com.frotafantasma.analytics.recommendation;

import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class AutomaticAwardsServiceTest {
    @Test void mondayBoundaryUsesSaoPauloNotUtcMidnight() {
        assertThat(AutomaticAwardsService.closingWeek(Instant.parse("2026-10-19T08:59:59Z"))).isEqualTo(LocalDate.parse("2026-10-12"));
        assertThat(AutomaticAwardsService.closingWeek(Instant.parse("2026-10-19T09:00:00Z"))).isEqualTo(LocalDate.parse("2026-10-19"));
        assertThat(AutomaticAwardsService.closingWeek(Instant.parse("2026-10-22T12:00:00Z"))).isEqualTo(LocalDate.parse("2026-10-19"));
    }
}
