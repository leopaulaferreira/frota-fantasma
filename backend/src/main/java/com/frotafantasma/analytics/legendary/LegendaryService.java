package com.frotafantasma.analytics.legendary;

import static com.frotafantasma.analytics.legendary.LegendaryDtos.*;
import com.frotafantasma.analytics.client.*;
import com.frotafantasma.analytics.config.ClashRoyaleProperties;
import com.frotafantasma.analytics.config.CollectionProperties;
import com.frotafantasma.analytics.dto.clash.PlayerProfileDto;
import com.frotafantasma.analytics.recommendation.AutomaticAwardsService;
import java.sql.*;
import java.time.*;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
@EnableConfigurationProperties(LegendaryProperties.class)
public class LegendaryService {
    private final CollectionProperties collection;
    private final LegendaryRepository repository;
    private final LegendaryProperties properties;
    private final ClashRoyaleProperties api;
    private final ClashRoyaleClient client;
    private final Clock clock;
    private final DataSource database;
    private final TransactionTemplate transaction;
    public LegendaryService(LegendaryRepository repository, LegendaryProperties properties, ClashRoyaleProperties api,
                            ClashRoyaleClient client, Clock clock, DataSource database, PlatformTransactionManager transactions, CollectionProperties collection) {
        this.collection=collection; this.repository=repository; this.properties=properties; this.api=api; this.client=client;
        this.clock=clock; this.database=database; this.transaction=new TransactionTemplate(transactions);
    }
    public Overview read(LocalDate requestedWeek, int page) {
        var weeks=repository.weeks(api.clanTag(),page);
        LocalDate selected=requestedWeek!=null ? requestedWeek : weeks.isEmpty() ? null : weeks.getFirst();
        Result result=selected==null ? null : result(selected);
        Instant close=close(AutomaticAwardsService.closingWeek(clock.instant()));
        boolean finalized=repository.cycle(api.clanTag(),AutomaticAwardsService.closingWeek(clock.instant())).map(c->c.finalizedAt()!=null).orElse(false);
        Instant next=!finalized && clock.instant().isBefore(close.plus(properties.captureWindow())) ? close : close.plus(Duration.ofDays(7));
        return new Overview(properties.enabled() && collection.enabled(),properties.official(),properties.recordSource(),properties.recordEra(),
                next,weeks,page,repository.count(api.clanTag()),result);
    }
    public Result result(LocalDate week) {
        return repository.stored(api.clanTag(),week).orElseGet(()-> {
            Cycle c=repository.cycle(api.clanTag(),week).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Ciclo ainda não coletado"));
            return new Result(c,candidates(c,repository.war(api.clanTag(),c)),
                    "Coleta em andamento ou aguardando a guerra oficial. Ainda não há vencedor confirmado.");
        });
    }
    /** Session lock covers HTTP without holding a database transaction; successes survive process restarts. */
    public void collect() {
        if(!properties.enabled()) return;
        try(Connection connection=database.getConnection()) {
            if(!lock(connection,true)) return;
            try {
                LocalDate week=AutomaticAwardsService.closingWeek(clock.instant());
                Instant close=close(week);
                if(!close.isBefore(properties.eraStart()) && repository.cycle(api.clanTag(),week).isEmpty()) {
                    Instant now=clock.instant();
                    var c=new Cycle(week,close,close.plus(properties.captureWindow()),now,null,"COLLECTING",
                            properties.recordSource(),properties.recordEra(),properties.eraStart(),properties.trophyLimit(),properties.official(),null,null);
                    transaction.executeWithoutResult(tx->repository.create(api.clanTag(),c));
                }
                for(var cycle:repository.pending(api.clanTag())) process(cycle);
            } finally { lock(connection,false); }
        } catch(SQLException e) { throw new IllegalStateException("Não foi possível obter o bloqueio de coleta da Ladder",e); }
    }
    public boolean needsWar() {
        return properties.enabled() && repository.pending(api.clanTag()).stream()
                .anyMatch(c->clock.instant().isBefore(c.closesAt().plus(Duration.ofDays(7))));
    }
    private void process(Cycle c) {
        for(var s:repository.snapshots(api.clanTag(),c.week())) {
            if(s.capturedAt()!=null || !clock.instant().isBefore(c.deadline())) continue;
            try {
                var p=client.getPlayer(s.tag());
                Instant at=clock.instant();
                if(!at.isBefore(c.deadline())) { repository.failed(api.clanTag(),c.week(),s.tag(),"CAPTURE_WINDOW_EXPIRED"); continue; }
                String invalid=validate(c,s,p);
                if(invalid!=null) { repository.failed(api.clanTag(),c.week(),s.tag(),invalid); continue; }
                Integer high=repository.highWater(api.clanTag(),c,s.tag());
                Integer observed=repository.observedPeak(api.clanTag(),c,s.tag(),at);
                int record=c.recordSource()==LegendaryProperties.Source.OFFICIAL_API ? p.bestTrophies()
                        : Math.max(p.trophies(),Math.max(high==null ? 0 : high,observed==null ? 0 : observed));
                if(record>c.trophyLimit() || (high!=null && record<high)) {
                    repository.failed(api.clanTag(),c.week(),s.tag(),"RECORD_ERA_REVIEW_REQUIRED"); continue;
                }
                repository.captured(api.clanTag(),c,new Snapshot(s.tag(),p.name(),s.role(),s.fullCycle(),p.trophies(),
                        p.bestTrophies(),record,at,p.clan()==null ? null : p.clan().tag(),null,s.attempts()+1));
            } catch(ClashRoyaleException e) {
                repository.failed(api.clanTag(),c.week(),s.tag(),e.code());
                if("UPSTREAM_RATE_LIMITED".equals(e.code())) break;
            }
        }
        var snapshots=repository.snapshots(api.clanTag(),c.week());
        boolean allCaptured=!snapshots.isEmpty() && snapshots.stream().allMatch(s->s.capturedAt()!=null);
        Long war=repository.war(api.clanTag(),c);
        Instant now=clock.instant();
        boolean expired=!now.isBefore(c.deadline());
        if(!allCaptured && !expired) return;
        if(war==null && !snapshots.isEmpty() && now.isBefore(c.closesAt().plus(Duration.ofDays(7)))) {
            repository.awaitingWar(api.clanTag(),c.week()); return;
        }
        var candidates=candidates(c,war);
        // Missing evidence can conceal a better candidate: never finalize a partial winner.
        boolean complete=allCaptured && war!=null;
        String winner=complete ? candidates.stream().filter(Candidate::eligible).map(Candidate::tag).findFirst().orElse(null) : null;
        String status=complete ? "COMPLETE" : snapshots.stream().noneMatch(s->s.capturedAt()!=null) ? "MISSED" : "INCOMPLETE";
        String notice=!complete ? "Ciclo sem resultado: coleta incompleta ou guerra oficial indisponível. Dados ausentes não valem zero."
                : winner==null ? "Sem vencedor: nenhum membro cumpriu todos os critérios com uma superação positiva. A primeira coleta estabelece a base."
                : c.official() ? "Resultado oficial registrado para este ciclo." : "Resultado experimental. Não integra os títulos nem as mensagens oficiais do clã.";
        var finalCycle=new Cycle(c.week(),c.closesAt(),c.deadline(),c.createdAt(),now,status,c.recordSource(),c.recordEra(),c.eraStart(),c.trophyLimit(),c.official(),war,winner);
        transaction.executeWithoutResult(tx->repository.finish(api.clanTag(),new Result(finalCycle,candidates,notice)));
    }
    private String validate(Cycle c, Snapshot expected, PlayerProfileDto p) {
        if(!expected.tag().equals(p.tag()) || p.name()==null || p.name().isBlank() || p.name().length()>255
                || p.trophies()==null || p.trophies()<0) return "INVALID_PLAYER_PROFILE";
        if(p.clan()!=null && (p.clan().tag()==null || !p.clan().tag().matches("#[A-Z0-9]{3,31}"))) return "INVALID_PLAYER_CLAN";
        if(p.trophies()>c.trophyLimit()) return "TROPHY_ROAD_LIMIT_REVIEW_REQUIRED";
        if(p.bestTrophies()!=null && p.bestTrophies()<0) return "INVALID_API_RECORD";
        if(c.recordSource()==LegendaryProperties.Source.OFFICIAL_API && (p.bestTrophies()==null
                || p.bestTrophies()<p.trophies() || p.bestTrophies()>c.trophyLimit())) return "INVALID_API_RECORD";
        return null;
    }
    private List<Candidate> candidates(Cycle c, Long war) {
        var scores=repository.scores(war);
        return LegendaryCalculator.rank(repository.snapshots(api.clanTag(),c.week()).stream().map(s->
                LegendaryCalculator.candidate(s,repository.previous(api.clanTag(),c,s.tag()),repository.highWater(api.clanTag(),c,s.tag()),
                        scores.get(s.tag()),api.clanTag())).toList());
    }
    private static Instant close(LocalDate week) { return week.atTime(6,0).atZone(AutomaticAwardsService.ZONE).toInstant(); }
    private boolean lock(Connection c, boolean acquire) throws SQLException {
        try(var statement=c.prepareStatement(acquire ? "select pg_try_advisory_lock(hashtextextended(?,0))" : "select pg_advisory_unlock(hashtextextended(?,0))")) {
            statement.setString(1,"frota:legendary:"+api.clanTag());
            try(var result=statement.executeQuery()) { return result.next() && result.getBoolean(1); }
        }
    }
}
