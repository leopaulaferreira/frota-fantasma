package com.frotafantasma.analytics.scheduler;

import com.frotafantasma.analytics.recommendation.AutomaticAwardsService;
import com.frotafantasma.analytics.service.*;
import com.frotafantasma.analytics.dto.CollectionResult;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class AutomaticAwardsSchedulerTest {
    @Test void failedCollectionIsRetriedAndCompletedBatchDoesNotCollectAgain() {
        var awards=mock(AutomaticAwardsService.class);
        var collections=mock(CollectionCoordinator.class);
        var scheduler=new AutomaticAwardsScheduler(awards,collections);
        when(awards.pending()).thenReturn(true,true,false);
        when(collections.collect(CollectionKind.WEEKLY_CLOSE,null)).thenReturn(
                new CollectionResult(null,"WEEKLY_CLOSE","FAILED",null,null,null,"UPSTREAM"),
                new CollectionResult(null,"WEEKLY_CLOSE","SUCCESS",null,null,1,null));
        when(collections.collect(CollectionKind.MEMBERS,null)).thenReturn(
                new CollectionResult(null,"MEMBERS","SUCCESS",null,null,3,null));
        scheduler.closing();
        verify(awards,never()).publish();
        scheduler.retry();
        verify(awards).publish();
        scheduler.retry();
        verify(collections,times(2)).collect(CollectionKind.WEEKLY_CLOSE,null);
        verify(collections,times(1)).collect(CollectionKind.MEMBERS,null);
    }
}
