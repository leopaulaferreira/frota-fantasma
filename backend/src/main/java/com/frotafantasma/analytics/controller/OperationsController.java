package com.frotafantasma.analytics.controller;

import com.frotafantasma.analytics.config.*;
import com.frotafantasma.analytics.dto.CollectionResult;
import java.time.*;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/operations")
public class OperationsController {
    public record Source(String name,Instant lastSuccess,Long ageSeconds,long expectedIntervalSeconds,
                         boolean stale,long failuresLast24Hours,CollectionResult latest) {}
    public record Status(Instant generatedAt,boolean collectionEnabled,List<Source> sources) {}
    private final JdbcTemplate sql;
    private final ClashRoyaleProperties api;
    private final CollectionProperties config;
    private final Clock clock;
    public OperationsController(JdbcTemplate sql,ClashRoyaleProperties api,CollectionProperties config,Clock clock) {
        this.sql=sql;this.api=api;this.config=config;this.clock=clock;
    }
    @GetMapping public Status status() {
        Instant now=clock.instant();
        return new Status(now,config.enabled(),List.of(
                source("MEMBERS",List.of("MEMBERS","DAILY_MEMBERS"),config.membersInterval(),now),
                source("RIVER",List.of("RIVER","DAILY_RIVER"),config.warInterval(),now),
                source("WAR_LOG",List.of("WAR_LOG","WEEKLY_CLOSE"),config.logInterval(),now)));
    }
    private Source source(String name,List<String> kinds,Duration interval,Instant now) {
        var latest=sql.query("""
                select id,kind,status,started_at,completed_at,record_count,error_code
                from collection_run where clan_tag=? and kind in (?,?) order by started_at desc,id desc limit 1
                """,(r,n)->new CollectionResult(r.getObject(1,java.util.UUID.class),r.getString(2),r.getString(3),r.getTimestamp(4).toInstant(),
                r.getTimestamp(5)==null?null:r.getTimestamp(5).toInstant(),(Integer)r.getObject(6),r.getString(7)),api.clanTag(),kinds.get(0),kinds.get(1));
        var success=sql.queryForObject("""
                select max(completed_at) from collection_run where clan_tag=? and kind in (?,?) and status='SUCCESS'
                """,java.sql.Timestamp.class,api.clanTag(),kinds.get(0),kinds.get(1));
        Instant last=success==null?null:success.toInstant();
        long failures=sql.queryForObject("""
                select count(*) from collection_run where clan_tag=? and kind in (?,?) and status='FAILED' and started_at>=?
                """,Long.class,api.clanTag(),kinds.get(0),kinds.get(1),java.sql.Timestamp.from(now.minus(Duration.ofDays(1))));
        Long age=last==null?null:Math.max(0,Duration.between(last,now).getSeconds());
        return new Source(name,last,age,interval.getSeconds(),age==null||age>interval.multipliedBy(2).getSeconds(),failures,latest.isEmpty()?null:latest.getFirst());
    }
}
