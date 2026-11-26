package com.frotafantasma.analytics.legendary;

import static org.mockito.Mockito.*;
import com.frotafantasma.analytics.service.*;
import org.junit.jupiter.api.Test;

class LegendarySchedulerTest {
    @Test void coletaPerfisAntesDeEsperarGuerra() {
        var service=mock(LegendaryService.class);var collections=mock(CollectionCoordinator.class);
        when(service.needsWar()).thenReturn(true);
        new LegendaryScheduler(service,collections).closing();
        var order=inOrder(service,collections);order.verify(service).collect();order.verify(service).needsWar();
        order.verify(collections).collect(CollectionKind.WEEKLY_CLOSE,null);order.verify(service).collect();
    }
    @Test void cicloFinalizadoNaoRepeteConsultaExterna() {
        var service=mock(LegendaryService.class);var collections=mock(CollectionCoordinator.class);
        new LegendaryScheduler(service,collections).retry();verifyNoInteractions(collections);
    }
    @Test void falhaNaoDerrubaAgendamento() {
        var service=mock(LegendaryService.class);var collections=mock(CollectionCoordinator.class);
        doThrow(new IllegalStateException()).when(service).collect();
        new LegendaryScheduler(service,collections).retry();verifyNoInteractions(collections);
    }
}
