package com.frotafantasma.analytics.security;
import static org.assertj.core.api.Assertions.*;
import java.time.*;
import org.junit.jupiter.api.Test;
class LoginThrottleTest {
    @Test void limitaDezFalhasELiberaAposJanelaOuSucesso() {
        Instant now=Instant.parse("2026-10-13T00:00:00Z");
        var throttle=new LoginThrottle(Clock.fixed(now,ZoneOffset.UTC));
        for(int i=0;i<9;i++)throttle.failed("ip");
        assertThat(throttle.retryAfter("ip")).isZero();
        throttle.failed("ip");assertThat(throttle.retryAfter("ip")).isEqualTo(300);
        assertThat(throttle.retryAfter("other")).isZero();
        throttle.succeeded("ip");assertThat(throttle.retryAfter("ip")).isZero();
    }
}
