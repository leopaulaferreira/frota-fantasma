package com.frotafantasma.analytics.scheduler;

import com.frotafantasma.analytics.config.ClashRoyaleProperties;
import com.frotafantasma.analytics.config.CollectionProperties;
import com.frotafantasma.analytics.service.CollectionCoordinator;
import com.frotafantasma.analytics.service.CollectionKind;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "collection.enabled", havingValue = "true", matchIfMissing = true)
public class CollectionScheduler {
    private final CollectionCoordinator coordinator;
    private final CollectionProperties settings;
    private final ClashRoyaleProperties api;
    private final Clock clock;

    public CollectionScheduler(CollectionCoordinator coordinator, CollectionProperties settings, ClashRoyaleProperties api, Clock clock) {
        this.coordinator = coordinator; this.settings = settings; this.api = api; this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${collection.members-interval:PT30M}", initialDelayString = "${collection.initial-delay:PT10S}")
    public void members() { collect(CollectionKind.MEMBERS, bucket(settings.membersInterval())); }

    @Scheduled(fixedDelayString = "${collection.war-interval:PT15M}", initialDelayString = "${collection.initial-delay:PT10S}")
    public void river() {
        if (settings.warDays().contains(LocalDate.now(clock.withZone(settings.timezone())).getDayOfWeek())) {
            collect(CollectionKind.RIVER, bucket(settings.warInterval()));
        }
    }

    @Scheduled(fixedDelayString = "${collection.log-interval:PT6H}", initialDelayString = "${collection.initial-delay:PT10S}")
    public void warLog() { collect(CollectionKind.WAR_LOG, bucket(settings.logInterval())); }

    @Scheduled(cron = "${collection.daily-cron:0 5 0 * * *}", zone = "${collection.timezone:UTC}")
    public void daily() {
        String day = LocalDate.now(clock.withZone(settings.timezone())).toString();
        collect(CollectionKind.DAILY_MEMBERS, day);
        collect(CollectionKind.DAILY_RIVER, day);
    }

    @Scheduled(cron = "${collection.weekly-cron:0 0 12 * * MON}", zone = "${collection.timezone:UTC}")
    public void weekly() {
        collect(CollectionKind.WEEKLY_CLOSE, LocalDate.now(clock.withZone(settings.timezone())).toString());
    }

    // Explicit São Paulo schedule: the general UTC war-day schedule ends at 21h local on Sunday.
    @Scheduled(cron = "0 */5 18-21 * * SUN", zone = "America/Sao_Paulo")
    @Scheduled(cron = "0 59 21 * * SUN", zone = "America/Sao_Paulo")
    public void weeklyReview() {
        var local = clock.instant().atZone(java.time.ZoneId.of("America/Sao_Paulo"));
        if (local.getDayOfWeek() != java.time.DayOfWeek.SUNDAY || local.getHour() < 18 || local.getHour() >= 22) return;
        String minute = "REVIEW:" + local.toLocalDate() + ":" + local.getHour() + ":" + local.getMinute();
        collect(CollectionKind.MEMBERS, minute);
        collect(CollectionKind.RIVER, minute);
    }

    private String bucket(Duration interval) { return Long.toString(clock.instant().getEpochSecond() / interval.toSeconds()); }
    private void collect(CollectionKind kind, String bucket) {
        try {
            coordinator.collect(kind, api.clanTag() + ":" + kind + ":" + bucket);
        } catch (RuntimeException error) {
            // Avoid logging credentials or upstream content, even when the database is unavailable.
            LoggerFactory.getLogger(CollectionScheduler.class).warn("Collection {} could not be recorded", kind);
        }
    }
}
