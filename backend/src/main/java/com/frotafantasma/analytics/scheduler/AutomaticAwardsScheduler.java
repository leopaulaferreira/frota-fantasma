package com.frotafantasma.analytics.scheduler;

import com.frotafantasma.analytics.recommendation.AutomaticAwardsService;
import com.frotafantasma.analytics.service.CollectionCoordinator;
import com.frotafantasma.analytics.service.CollectionKind;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name={"collection.enabled", "awards.automatic.enabled"}, havingValue="true", matchIfMissing=true)
public class AutomaticAwardsScheduler {
    private final AutomaticAwardsService awards;
    private final CollectionCoordinator collections;
    public AutomaticAwardsScheduler(AutomaticAwardsService awards, CollectionCoordinator collections) {
        this.awards=awards; this.collections=collections;
    }
    @Scheduled(cron="0 0 6 * * MON", zone="America/Sao_Paulo")
    public void closing() { attempt(); }
    @Scheduled(fixedDelayString="${awards.automatic.retry-interval:PT15M}", initialDelayString="${collection.initial-delay:PT10S}")
    public void retry() { attempt(); }
    private void attempt() {
        try {
            if (!awards.pending()) return;
            if (!"SUCCESS".equals(collections.collect(CollectionKind.WEEKLY_CLOSE, null).status())) return;
            if (!"SUCCESS".equals(collections.collect(CollectionKind.MEMBERS, null).status())) return;
            awards.publish();
        } catch (RuntimeException ex) {
            LoggerFactory.getLogger(getClass()).warn("Automatic awards pending; will retry without changing existing awards");
        }
    }
}
