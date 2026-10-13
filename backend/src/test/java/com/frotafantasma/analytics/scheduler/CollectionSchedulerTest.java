package com.frotafantasma.analytics.scheduler;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.frotafantasma.analytics.config.ClashRoyaleProperties;
import com.frotafantasma.analytics.config.CollectionProperties;
import com.frotafantasma.analytics.service.CollectionCoordinator;
import com.frotafantasma.analytics.service.CollectionKind;
import java.time.*;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CollectionSchedulerTest {
    final CollectionCoordinator coordinator = mock(CollectionCoordinator.class);
    final ClashRoyaleProperties api = new ClashRoyaleProperties("https://api.clashroyale.com/v1", "#RQJGC9GV", "test-token",
            Duration.ofSeconds(5), Duration.ofSeconds(10), 2, Duration.ofMillis(500), Duration.ofSeconds(30));
    CollectionScheduler scheduler(String instant) {
        var properties = new CollectionProperties(true, Duration.ofMinutes(30), Duration.ofMinutes(15), Duration.ofHours(6),
                Duration.ofSeconds(10), ZoneId.of("America/Sao_Paulo"),
                Set.of(DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), 20);
        return new CollectionScheduler(coordinator, properties, api, Clock.fixed(Instant.parse(instant), ZoneOffset.UTC));
    }
    @Test void respeitaDiasDeGuerraEFusoConfigurado() {
        scheduler("2026-10-08T00:30:00Z").river();
        verifyNoInteractions(coordinator);
        scheduler("2026-10-08T03:01:00Z").river();
        verify(coordinator).collect(eq(CollectionKind.RIVER), startsWith("#RQJGC9GV:RIVER:"));
    }
    @Test void usaChavesEstaveisParaJobsPeriodicos() {
        var scheduler = scheduler("2026-10-08T12:00:00Z");
        scheduler.members(); scheduler.members(); scheduler.warLog();
        verify(coordinator, times(2)).collect(eq(CollectionKind.MEMBERS), eq("#RQJGC9GV:MEMBERS:" + (Instant.parse("2026-10-08T12:00:00Z").getEpochSecond() / 1800)));
        verify(coordinator).collect(eq(CollectionKind.WAR_LOG), startsWith("#RQJGC9GV:WAR_LOG:"));
    }
    @Test void diarioColetaAmbasFontesMesmoQuandoUmaFalha() {
        when(coordinator.collect(eq(CollectionKind.DAILY_MEMBERS), anyString())).thenThrow(new IllegalStateException());
        scheduler("2026-10-08T12:00:00Z").daily();
        verify(coordinator).collect(CollectionKind.DAILY_MEMBERS, "#RQJGC9GV:DAILY_MEMBERS:2026-10-08");
        verify(coordinator).collect(CollectionKind.DAILY_RIVER, "#RQJGC9GV:DAILY_RIVER:2026-10-08");
    }
    @Test void fechamentoSemanalConsultaApenasResultadosOficiais() {
        scheduler("2026-10-12T12:00:00Z").weekly();
        verify(coordinator).collect(CollectionKind.WEEKLY_CLOSE, "#RQJGC9GV:WEEKLY_CLOSE:2026-10-12");
    }
}
