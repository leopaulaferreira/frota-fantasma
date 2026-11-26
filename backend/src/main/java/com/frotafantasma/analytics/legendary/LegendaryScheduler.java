package com.frotafantasma.analytics.legendary;

import com.frotafantasma.analytics.service.CollectionCoordinator;
import com.frotafantasma.analytics.service.CollectionKind;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name={"collection.enabled","awards.legendary.enabled"},havingValue="true",matchIfMissing=true)
public class LegendaryScheduler {
    private final LegendaryService legendary;
    private final CollectionCoordinator collections;
    public LegendaryScheduler(LegendaryService legendary, CollectionCoordinator collections) { this.legendary=legendary; this.collections=collections; }
    @Scheduled(cron="0 0 6 * * MON",zone="America/Sao_Paulo")
    public void closing() { attempt(); }
    @Scheduled(fixedDelayString="${awards.legendary.retry-interval:PT15M}",initialDelayString="${collection.initial-delay:PT10S}")
    public void retry() { attempt(); }
    public void attempt() {
        try {
            // Capture profiles at the boundary, even if the official war log is not published yet.
            legendary.collect();
            if(legendary.needsWar()) {
                collections.collect(CollectionKind.WEEKLY_CLOSE,null);
                legendary.collect();
            }
        } catch(RuntimeException e) {
            LoggerFactory.getLogger(getClass()).warn("Legendary award collection pending; saved evidence preserved ({}).",e.getClass().getSimpleName());
        }
    }
}
