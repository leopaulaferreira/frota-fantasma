package com.frotafantasma.analytics.legendary;

import static com.frotafantasma.analytics.legendary.LegendaryDtos.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LegendaryRepository {
    private final JdbcTemplate sql;
    private final ObjectMapper json;
    public LegendaryRepository(JdbcTemplate sql, ObjectMapper json) { this.sql = sql; this.json = json; }
    private static Timestamp ts(Instant at) { return at == null ? null : Timestamp.from(at); }
    private static Instant instant(ResultSet r, String name) throws SQLException {
        Timestamp t = r.getTimestamp(name); return t == null ? null : t.toInstant();
    }
    private Cycle cycle(ResultSet r, int row) throws SQLException {
        return new Cycle(r.getObject("week", LocalDate.class), instant(r,"closes_at"), instant(r,"deadline"),
                instant(r,"created_at"), instant(r,"finalized_at"), r.getString("status"),
                LegendaryProperties.Source.valueOf(r.getString("record_source")), r.getString("record_era"),
                instant(r,"era_start"), r.getInt("trophy_limit"), r.getBoolean("official"),
                r.getObject("war_id",Long.class), r.getString("winner_tag"));
    }
    private Snapshot snapshot(ResultSet r, int row) throws SQLException {
        return new Snapshot(r.getString("player_tag"), r.getString("player_name"), r.getString("role"),
                r.getBoolean("full_cycle"), r.getObject("trophies",Integer.class), r.getObject("api_best_trophies",Integer.class),
                r.getObject("record_trophies",Integer.class), instant(r,"captured_at"), r.getString("profile_clan_tag"),
                r.getString("error_code"),r.getInt("attempts"));
    }
    public Optional<Cycle> cycle(String clan, LocalDate week) {
        return sql.query("select * from legendary_cycle where clan_tag=? and week=?",this::cycle,clan,week).stream().findFirst();
    }
    public List<Cycle> pending(String clan) {
        return sql.query("select * from legendary_cycle where clan_tag=? and finalized_at is null order by week",this::cycle,clan);
    }
    public List<LocalDate> weeks(String clan, int page) {
        return sql.query("select week from legendary_cycle where clan_tag=? order by week desc limit 52 offset ?",(r,n)->r.getObject(1,LocalDate.class),clan,(long)page*52);
    }
    public long count(String clan) { return sql.queryForObject("select count(*) from legendary_cycle where clan_tag=?",Long.class,clan); }
    public void create(String clan, Cycle c) {
        sql.update("""
            insert into legendary_cycle(clan_tag,week,closes_at,deadline,created_at,status,record_source,record_era,era_start,trophy_limit,official)
            values (?,?,?,?,?,?,?,?,?,?,?)
            """,clan,c.week(),ts(c.closesAt()),ts(c.deadline()),ts(c.createdAt()),c.status(),c.recordSource().name(),c.recordEra(),ts(c.eraStart()),c.trophyLimit(),c.official());
        // Freeze the last DIRECT_API roster before closure, never an imported or current roster from days later.
        sql.update("""
            insert into legendary_snapshot(clan_tag,week,player_tag,player_name,role,full_cycle)
            select ?,?,p.player_tag,coalesce(h.name,p.player_tag),h.role,
                exists(select 1 from clan_membership_history b where b.clan_id=h.clan_id and b.player_id=h.player_id
                    and b.source='DIRECT_API' and b.captured_at<=? and b.captured_at>=?)
                and not exists(select 1 from membership_event e where e.clan_id=h.clan_id and e.player_id=h.player_id
                    and e.type in ('JOIN','LEAVE') and e.observed_at>? and e.observed_at<=?)
            from clan_membership_history h join clan c on c.id=h.clan_id join player p on p.id=h.player_id
            where c.tag=? and h.source='DIRECT_API' and h.captured_at=(
                select max(b.captured_at) from clan_membership_history b where b.clan_id=h.clan_id
                and b.source='DIRECT_API' and b.captured_at<=?) and h.captured_at>=?
            """,clan,c.week(),ts(c.closesAt().minus(Duration.ofDays(7))),ts(c.closesAt().minus(Duration.ofDays(7)).minus(Duration.ofHours(2))),
                ts(c.closesAt().minus(Duration.ofDays(7))),ts(c.createdAt()),clan,ts(c.closesAt()),ts(c.closesAt().minus(Duration.ofHours(2))));
    }
    public List<Snapshot> snapshots(String clan, LocalDate week) {
        return sql.query("select * from legendary_snapshot where clan_tag=? and week=? order by player_tag",this::snapshot,clan,week);
    }
    private Object[] historyArgs(String clan, Cycle c, String tag) {
        return new Object[]{clan,tag,c.week(),c.recordSource().name(),c.recordEra(),ts(c.eraStart())};
    }
    private static final String HISTORY = """
        from legendary_snapshot s join legendary_cycle c using(clan_tag,week)
        where s.clan_tag=? and s.player_tag=? and s.week<? and c.record_source=? and c.record_era=? and c.era_start=?
        """;
    public Integer highWater(String clan, Cycle c, String tag) {
        return sql.queryForObject("select max(s.record_trophies) " + HISTORY,Integer.class,historyArgs(clan,c,tag));
    }
    public Snapshot previous(String clan, Cycle c, String tag) {
        return sql.query("select s.* " + HISTORY + " and s.week=(?::date - 7)",this::snapshot,
                clan,tag,c.week(),c.recordSource().name(),c.recordEra(),ts(c.eraStart()),c.week()).stream().findFirst().orElse(null);
    }
    public Integer observedPeak(String clan, Cycle c, String tag, Instant at) {
        return sql.queryForObject("""
            select max(h.trophies) from clan_membership_history h join player p on p.id=h.player_id join clan c on c.id=h.clan_id
            where c.tag=? and p.player_tag=? and h.source='DIRECT_API' and h.captured_at>=? and h.captured_at<=?
                and h.trophies>=0
            """,Integer.class,clan,tag,ts(c.eraStart()),ts(at));
    }
    public void captured(String clan, Cycle c, Snapshot s) {
        sql.update("""
            update legendary_snapshot set player_name=?,trophies=?,api_best_trophies=?,record_trophies=?,captured_at=?,profile_clan_tag=?,
                error_code=null,attempts=attempts+1,full_cycle=full_cycle and not exists(
                    select 1 from membership_event e join player p on p.id=e.player_id join clan c on c.id=e.clan_id
                    where c.tag=? and p.player_tag=? and e.type in ('JOIN','LEAVE') and e.observed_at>? and e.observed_at<=?)
            where clan_tag=? and week=? and player_tag=? and captured_at is null
            """,s.name(),s.trophies(),s.apiBestTrophies(),s.recordTrophies(),ts(s.capturedAt()),s.profileClanTag(),
                clan,s.tag(),ts(c.closesAt().minus(Duration.ofDays(7))),ts(s.capturedAt()),clan,c.week(),s.tag());
    }
    public void failed(String clan, LocalDate week, String tag, String code) {
        sql.update("update legendary_snapshot set error_code=?,attempts=attempts+1 where clan_tag=? and week=? and player_tag=? and captured_at is null",code,clan,week,tag);
    }
    public Long war(String clan, Cycle c) {
        var ids=sql.query("""
            select w.id from war w join clan c on c.id=w.clan_id where c.tag=? and w.source='DIRECT_API'
                and w.published_at>=? and w.published_at<? order by w.published_at
            """,(r,n)->r.getLong(1),clan,ts(c.closesAt()),ts(c.closesAt().plus(Duration.ofDays(7))));
        return ids.size()==1 ? ids.getFirst() : null;
    }
    public Map<String,WarScore> scores(Long war) {
        Map<String,WarScore> result=new HashMap<>();
        if(war!=null) sql.query("""
            select p.player_tag,w.contribution,w.decks_used from war_participant w join player p on p.id=w.player_id
            where w.war_id=? and w.source='DIRECT_API'
            """,r->{result.put(r.getString(1),new WarScore(r.getObject(2,Integer.class),r.getObject(3,Integer.class)));},war);
        return result;
    }
    public void awaitingWar(String clan, LocalDate week) {
        sql.update("update legendary_cycle set status='AWAITING_WAR' where clan_tag=? and week=?",clan,week);
    }
    public void finish(String clan, Result result) {
        var c=result.cycle();
        sql.update("update legendary_cycle set status=?,finalized_at=?,war_id=?,winner_tag=?,result=?::jsonb where clan_tag=? and week=? and finalized_at is null",
                c.status(),ts(c.finalizedAt()),c.warId(),c.winnerTag(),encode(result),clan,c.week());
    }
    public Optional<Result> stored(String clan, LocalDate week) {
        return sql.query("select result from legendary_cycle where clan_tag=? and week=? and result is not null",(r,n)->decode(r.getString(1)),clan,week).stream().findFirst();
    }
    private String encode(Result result) {
        try { return json.writeValueAsString(result); } catch(JsonProcessingException e) { throw new IllegalStateException(e); }
    }
    private Result decode(String value) {
        try { return json.readValue(value,Result.class); } catch(JsonProcessingException e) { throw new IllegalStateException(e); }
    }
}
